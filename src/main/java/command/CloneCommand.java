package command;

import com.google.common.primitives.Bytes;
import constants.DeltaType;
import constants.ObjectType;
import object.Blob;
import object.Commit;
import object.DeltaInstructionObject;
import object.GitObject;
import object.PackObject;
import object.RawObject;
import object.Tree;
import object.TreeEntry;
import picocli.CommandLine;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static java.lang.System.exit;

@CommandLine.Command(name = "clone")
public class CloneCommand implements Callable {

    public static final int PACK_TYPE_MASK = 0b01110000;
    public static final int PACK_4_BIT_MASK = 0b00001111;
    public static final int PACK_7_BIT_MASK = 0b01111111;
    public static final int PACK_MSB_MASK = 0b10000000;

    public static Path git_root_dir;

    public static Map<String, String> head_map = new HashMap<>();

    public static ByteBuffer pack;

    @CommandLine.Parameters
    private String repo_url;

    @CommandLine.Parameters
    private String dir;

    @Override
    public Object call() {
        // https://www.git-scm.com/docs/http-protocol
        // step 1 - discovering references
        // make a GET request to $GIT_URL/info/refs, The request MUST contain exactly one query parameter, service=$servicename
        // C: GET $GIT_URL/info/refs?service=git-upload-pack HTTP/1.0

        git_root_dir = Path.of(dir).toAbsolutePath();

        try {
            byte[] response = sendRequest(repo_url);
            String s_response = new String(response);
            String[] responses = s_response.split("\n");

            Set<String> common = new HashSet();

            Map<String, String> advertised = parseResponse(responses);

            Set<String> want = new HashSet<>(advertised.values());

            byte[] write_buffer = constructWantRequest(want);

            byte[] want_response = sendWantRequest(repo_url, write_buffer);
            pack = extractPack(want_response);

            // parse pack to GitObject
            List<PackObject> packObjects = parsePack();

            // construct repo from packObjects
            // init git directory
            initCommand(dir);

            writePackObjects(packObjects);

            // build head commit
            Commit commit = (Commit) GitObject.fromHash(head_map.get("head_hash"), git_root_dir);
            Tree tree = (Tree) GitObject.fromHash(commit.getTree_hash(), git_root_dir);

            checkOut(tree, git_root_dir);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private static byte[] sendRequest(String repo_url) throws IOException {
        URL url = new URL(repo_url + "/info/refs?service=git-upload-pack");
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        if ((urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) && (urlConnection.getResponseCode() != HttpURLConnection.HTTP_NOT_MODIFIED)) {
            System.out.println("Connection Error: " + urlConnection.getResponseCode());
            exit(1);
        }

        InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
        return inputStream.readAllBytes();
    }

    private static Map<String, String> parseResponse(String[] responses) {
        Map<String, String> advertised = new HashMap<>();

        for (int i = 1; i< responses.length; i++) { // ignore first response: 001e# service=git-upload-pack
            String line = responses[i];
            if (line.startsWith("0000")) {
                line = line.substring(4);
            }
            if (line.length() == 0) continue;
            byte[] bytes = line.getBytes();
            int null_byte_idx = Bytes.indexOf(bytes, (byte) 0x00);
            if (null_byte_idx >= 0) {
                line = new String(Arrays.copyOfRange(bytes, 0, null_byte_idx));
            }
            line = line.substring(4);

            // 23f0bc3b5c7c3108e41c448f01a3db31e7064bbb refs/heads/master
            String[] hash_name = line.split(" ");
            advertised.put(hash_name[1], hash_name[0]);
            if (head_map.isEmpty()) {
                head_map.put("head_name", hash_name[1]);
                head_map.put("head_hash", hash_name[0]);
            }
        }

        return advertised;
    }

    private static byte[] constructWantRequest(Set<String> want) {
        byte[] write_buffer = new byte[0];
        for (String obj: want) {
            // format: 0032want 0a53e9ddeaddad63ad106860237bbf53411d11a7\n
            String to_write = "want " + obj;
            to_write += "\n";
            int length = to_write.getBytes().length + 4;
            write_buffer = Bytes.concat(write_buffer, String.format("%04x", length).getBytes(), to_write.getBytes());

        }
        write_buffer = Bytes.concat(write_buffer, "00000009done\n".getBytes());
        return write_buffer;
    }

    private static byte[] sendWantRequest(String repo_url, byte[] write_buffer) throws IOException {
        URL url = new URL(repo_url + "/git-upload-pack");
        HttpURLConnection postHttpURLConnection = (HttpURLConnection) url.openConnection();
        postHttpURLConnection.setDoOutput(true);
        postHttpURLConnection.setRequestMethod("POST");
        postHttpURLConnection.setRequestProperty("Content-Type", "application/x-git-upload-pack-request");
        try (DataOutputStream outputStream = new DataOutputStream(postHttpURLConnection.getOutputStream())) {
            outputStream.write(write_buffer);
        }

        InputStream inputStream = new BufferedInputStream(postHttpURLConnection.getInputStream());
        return inputStream.readAllBytes();
    }

    private static ByteBuffer extractPack(byte[] want_response) {
        int idx = Bytes.indexOf(want_response, new byte[]{'P', 'A', 'C', 'K'});
        return ByteBuffer.wrap(Arrays.copyOfRange(want_response, idx + 4, want_response.length));
    }

    private static List<PackObject> parsePack() throws DataFormatException {
        @SuppressWarnings("unused")
        int pack_version = pack.getInt();
        int n_packObjects = pack.getInt();

        List<PackObject> packObjects = new ArrayList<>();
        for (int i=0; i<n_packObjects; i++) {
            PackObject packObject = new PackObject();
            // parse object header
            // MSB + 3 bit type + 4 bit size
            // MSB + 7 bit size
            int first = Byte.toUnsignedInt(pack.get());
            int object_type = (first & PACK_TYPE_MASK) >> 4;
            int object_size = first & PACK_4_BIT_MASK;
            int continue_read = first & PACK_MSB_MASK;
            if (continue_read > 0) {
                object_size += (parseVariableLengthIntLittleEndian(pack) << 4);
            }

            switch (object_type) {
                case 1: // commit
                    packObject.setType(ObjectType.COMMIT);
                    packObject.setContent(getInflated(object_size));
                    break;
                case 2: // tree
                    packObject.setType(ObjectType.TREE);
                    packObject.setContent(getInflated(object_size));
                    break;
                case 3: // blob
                    packObject.setType(ObjectType.BLOB);
                    packObject.setContent(getInflated(object_size));
                    break;
                case 4: // tag
                    break;
                case 6: // OFS_DELTA
                    // not supported yet
                    break;
                case 7: // REF_DELTA
                    // in this case object_size is the delta data size;
                    // parse 20-byte base object name
                    byte[] baseObjectName = new byte[20];
                    pack.get(baseObjectName);
                    String baseHash = HexFormat.of().formatHex(baseObjectName);
                    packObject.setBaseHash(baseHash);
                    packObject.setType(ObjectType.DELTIFIED);

                    ByteBuffer deltaBuffer = ByteBuffer.wrap(getInflated(object_size));

                    // parse source and target data length. They are in little endian format
                    @SuppressWarnings("unused")
                    int src_size = parseVariableLengthIntLittleEndian(deltaBuffer);
                    int tar_size = parseVariableLengthIntLittleEndian(deltaBuffer);
                    packObject.setSize(tar_size);

                    packObject.setDeltaInstructionObjects(constructDeltaInstructionObjects(deltaBuffer));
            }

            packObjects.add(packObject);
        }
        return packObjects;
    }

    private static int parseVariableLengthIntLittleEndian(ByteBuffer pack) {
        int val = 0;
        int shift = 0;
        int continue_read = 1;

        while (continue_read > 0) {
            int first = Byte.toUnsignedInt(pack.get());
            val = val + ((first & PACK_7_BIT_MASK) << shift);
            continue_read = first & PACK_MSB_MASK;
            shift += 7;
        }

        return val;
    }

    private static byte[] getInflated(int object_size) throws DataFormatException {
        byte[] inflated = new byte[object_size];
        Inflater inflater = new Inflater();
        inflater.setInput(pack);
        inflater.inflate(inflated);
        return inflated;
        /*ByteBuffer dataBuffer = pack.slice();
        Inflater inflater = new Inflater();
        inflater.setInput(dataBuffer);

        ByteBuffer outputBuffer = ByteBuffer.allocate(1024 * 1024);
        inflater.inflate(outputBuffer);

        byte[] dataDecompressed = new byte[inflater.getTotalOut()];
        outputBuffer.rewind().get(dataDecompressed);
        pack.position(pack.position() + inflater.getTotalIn());
        return dataDecompressed;*/
    }

    private static List<DeltaInstructionObject> constructDeltaInstructionObjects(ByteBuffer deltaBuffer) {
        List<DeltaInstructionObject> deltaInstructionObjects = new ArrayList<>();
        while (deltaBuffer.hasRemaining()) {
            DeltaInstructionObject deltaInstructionObject = new DeltaInstructionObject();
            int read = deltaBuffer.get();
            int delta_type = read & PACK_MSB_MASK;

            if (delta_type == 0) { // insert
                deltaInstructionObject.setDelta_type(DeltaType.INSERT);
                int delta_size = read & PACK_7_BIT_MASK;
                byte[] insert_data = new byte[delta_size];
                deltaBuffer.get(insert_data);
                deltaInstructionObject.setDelta_data(insert_data);
            } else { // copy
                deltaInstructionObject.setDelta_type(DeltaType.COPY);
                // offset, size in little endian
                boolean offset1 = (read & 0b00000001) != 0;
                boolean offset2 = (read & 0b00000010) != 0;
                boolean offset3 = (read & 0b00000100) != 0;
                boolean offset4 = (read & 0b00001000) != 0;
                boolean size1 = (read & 0b00010000) != 0;
                boolean size2 = (read & 0b00100000) != 0;
                boolean size3 = (read & 0b01000000) != 0;

                int offset = parseVariableLengthInt(deltaBuffer, offset1, offset2, offset3, offset4);
                int size = parseVariableLengthInt(deltaBuffer, size1, size2, size3);
                if (size == 0) {
                    size = 0x10000;
                }
                deltaInstructionObject.setDelta_offset(offset);
                deltaInstructionObject.setDelta_size(size);
            }
            deltaInstructionObjects.add(deltaInstructionObject);
        }
        return deltaInstructionObjects;
    }

    private static int parseVariableLengthInt(ByteBuffer deltaBuffer, boolean... flags) {
        int val = 0;
        int shift = 0;

        for (boolean flag: flags) {
            if (flag) {
                val += ((Byte.toUnsignedInt(deltaBuffer.get())) << shift);
            }
            shift += 8;
        }

        return val;
    }

    private static void initCommand(String parent_dir) {
        final File root = new File(parent_dir);
        final File git_root = new File(root, ".git");
        git_root.mkdirs();
        new File(git_root, "objects").mkdirs();
        new File(git_root, "refs").mkdirs();
        final File head = new File(git_root, "HEAD");

        try {
            head.createNewFile();
            Files.write(head.toPath(), "ref: refs/heads/master\n".getBytes());
            System.out.println("Initialized git directory");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writePackObjects(List<PackObject> packObjects) {
        // write undeltified objects first
        for (PackObject object: packObjects) {
            ObjectType type = object.getType();
            if (type == ObjectType.DELTIFIED) continue;

            GitObject.writeRawObject(object.getContent(), type, git_root_dir);
        }

        // construct deltified objects
        for (PackObject object: packObjects) {
            ObjectType type = object.getType();
            if (type != ObjectType.DELTIFIED) continue;

            String baseHash = object.getBaseHash();
            RawObject baseObject = RawObject.fromHash(baseHash, git_root_dir);

            byte[] content = new byte[object.getSize()];
            ByteBuffer buffer = ByteBuffer.wrap(content);

            for (DeltaInstructionObject deltaInstructionObject: object.getDeltaInstructionObjects()) {
                if (deltaInstructionObject.getDelta_type() == DeltaType.COPY) {
                    buffer.put(baseObject.getContent(), deltaInstructionObject.getDelta_offset(), deltaInstructionObject.getDelta_size());
                } else if (deltaInstructionObject.getDelta_type() == DeltaType.INSERT) {
                    buffer.put(deltaInstructionObject.getDelta_data());
                }
            }

            if (buffer.hasRemaining()) {
                throw new IllegalStateException("buffer is not full");
            }

            GitObject.writeRawObject(content, baseObject.getType(), git_root_dir);
        }
    }

    private static void checkOut(Tree tree) {
        checkOut(tree, Path.of("").toAbsolutePath());
    }

    private static void checkOut(Tree tree, Path root_dir) {
        for (TreeEntry entry: tree.getEntries()) {
            switch (entry.getMode()) {
                case TreeEntry.EntryMode.DIRECTORY:
                    Tree subTree = (Tree) GitObject.fromHash(entry.getHash(), git_root_dir);
                    Path subRoot = root_dir.resolve(entry.getPath());
                    try {
                        Files.createDirectories(subRoot);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    checkOut(subTree, subRoot);
                    break;

                case TreeEntry.EntryMode.REGULAR_EXECUTABLE:
                case TreeEntry.EntryMode.REGULAR_NON_EXECUTABLE:
                    Blob blob = (Blob) GitObject.fromHash(entry.getHash(), git_root_dir);
                    Path path = root_dir.resolve(entry.getPath());

                    try {
                        Files.write(path, blob.toBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            }
        }
    }
}
