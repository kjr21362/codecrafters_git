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

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static java.lang.System.exit;
import static java.lang.System.out;

public class Main {

    public static final int PACK_TYPE_MASK = 0b01110000;
    public static final int PACK_4_BIT_MASK = 0b00001111;
    public static final int PACK_7_BIT_MASK = 0b01111111;
    public static final int PACK_MSB_MASK = 0b10000000;

    public static ByteBuffer pack;

    public static Path git_root_dir;

    public static void main(String[] args) {

        final String command = args[0];

        switch (command) {
            case "init" -> initCommand();
            case "cat-file" -> catFileCommand(args[1], args[2]);
            case "hash-object" -> System.out.print(hashObjectCommand(args[1], args[2]));
            case "ls-tree" -> lsTreeCommand(args[1], args[2]);
            case "write-tree" -> writeTreeCommand();
            case "commit-tree" -> commitTreeCommand(args[1], args[2], args[3], args[4], args[5]);
            case "clone" -> cloneCommand(args[1], args[2]);
            default -> System.out.println("Unknown command: " + command);
        }
    }

    private static void cloneCommand(String repo_url, String dir) {
        // https://www.git-scm.com/docs/http-protocol
        // step 1 - discovering references
        // make a GET request to $GIT_URL/info/refs, The request MUST contain exactly one query parameter, service=$servicename
        // C: GET $GIT_URL/info/refs?service=git-upload-pack HTTP/1.0

        //out.println("dir: " + dir);
        git_root_dir = Path.of(dir).toAbsolutePath();

        try {
            URL url = new URL(repo_url + "/info/refs?service=git-upload-pack");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            if ((urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) && (urlConnection.getResponseCode() != HttpURLConnection.HTTP_NOT_MODIFIED)) {
                System.out.println("Connection Error: " + urlConnection.getResponseCode());
                exit(1);
            }

            InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
            byte[] response = inputStream.readAllBytes();
            String s_response = new String(response);
            String[] responses = s_response.split("\n");
            //System.out.println(s_response);


            //Set<String> advertised = new HashSet();
            Map<String, String> advertised = new HashMap<>();
            Set<String> common = new HashSet();
            Set<String> want = new HashSet();
            Map<String, String> head_map = new HashMap<>();

            for (int i=1; i<responses.length; i++) { // ignore first response: 001e# service=git-upload-pack
                String line = responses[i];
                if (line.startsWith("0000")) {
                    line = line.substring(4);
                }
                if (line.length() == 0) continue;
                //System.out.println("line: " + line);
                byte[] bytes = line.getBytes();
                int null_byte_idx = Bytes.indexOf(bytes, (byte) 0x00);
                String caps = "";
                if (null_byte_idx >= 0) {
                    caps = new String(Arrays.copyOfRange(bytes, null_byte_idx + 1, bytes.length));
                    line = new String(Arrays.copyOfRange(bytes, 0, null_byte_idx));
                }
                line = line.substring(4);

                // 23f0bc3b5c7c3108e41c448f01a3db31e7064bbb refs/heads/master
                String[] hash_name = line.split(" ");
                //advertised.add(hash_name[0]);
                advertised.put(hash_name[1], hash_name[0]);
                if (head_map.isEmpty()) {
                    //out.println("hash_name[1]: " + hash_name[1]);
                    //out.println("hash_name[0]: " + hash_name[0]);
                    head_map.put("head_name", hash_name[1]);
                    head_map.put("head_hash", hash_name[0]);
                }
            }

            want = new HashSet(advertised.values());

            //StringBuffer buffer = new StringBuffer();
            byte[] write_buffer = new byte[0];
            //System.out.println("want: " + want);
            //String cap = "multi_ack_detailed no-done side-band-64k thin-pack deepen-since deepen-not agent=git/2.23.0";
            String cap = "";
            for (String obj: want) {
                // format: 0032want 0a53e9ddeaddad63ad106860237bbf53411d11a7\n
                String to_write = "want " + obj;
                if (cap.length() > 0) {
                    to_write += " " + cap;
                    cap = "";
                }
                to_write += "\n";
                int length = to_write.getBytes().length + 4;
                write_buffer = Bytes.concat(write_buffer, String.format("%04x", length).getBytes(), to_write.getBytes());

            }
            write_buffer = Bytes.concat(write_buffer, "00000009done\n".getBytes());
            //System.out.println("write_buffer: " + new String(write_buffer));

            url = new URL(repo_url + "/git-upload-pack");
            HttpURLConnection postHttpURLConnection = (HttpURLConnection) url.openConnection();
            postHttpURLConnection.setDoOutput(true);
            postHttpURLConnection.setRequestMethod("POST");
            postHttpURLConnection.setRequestProperty("Content-Type", "application/x-git-upload-pack-request");
            try (DataOutputStream outputStream = new DataOutputStream(postHttpURLConnection.getOutputStream())) {
                outputStream.write(write_buffer);
            }

            inputStream = new BufferedInputStream(postHttpURLConnection.getInputStream());
            response = inputStream.readAllBytes();
            s_response = new String(response);
            //System.out.println("s_response after write: " + s_response);
            //System.out.println(postHttpURLConnection.getResponseCode());
            int idx = Bytes.indexOf(response, new byte[]{'P', 'A', 'C', 'K'});
            pack = ByteBuffer.wrap(Arrays.copyOfRange(response, idx + 4, response.length));

            // parse pack to GitObject
            /*System.out.println("version: " + ByteBuffer.wrap(Arrays.copyOfRange(pack, 4, 8)).getInt());
            //System.out.println("n_objects: " + new String(Arrays.copyOfRange(pack, 8, 12)));
            System.out.println("n_objects: " + ByteBuffer.wrap(Arrays.copyOfRange(pack, 8, 12)).getInt());

            int n_packObjects = ByteBuffer.wrap(Arrays.copyOfRange(pack, 8, 12)).getInt();*/

            int pack_version = pack.getInt();
            int n_packObjects = pack.getInt();
            //System.out.println("pack_version: " + pack_version);
            //System.out.println("n_packObjects: " + n_packObjects);

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
                    /*first = Byte.toUnsignedInt(pack.get());
                    object_size = (object_size << 7) + (first & PACK_7_BIT_MASK);
                    continue_read = first & PACK_MSB_MASK;*/
                    object_size += (parseVariableLengthIntLittleEndian(pack) << 4);
                }

                //System.out.println("object_size: " + object_size);
                //System.out.println("object_type: " + object_type);

                // inflate content
                //byte[] inflated = getInflated(object_size, pack);

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
                        //System.out.println("baseHash: " + baseHash);

                        byte[] delta_data = getInflated(object_size);
                        ByteBuffer deltaBuffer = ByteBuffer.wrap(delta_data);

                        // parse source and target data length. They are in little endian format
                        int src_size = parseVariableLengthIntLittleEndian(deltaBuffer);
                        int tar_size = parseVariableLengthIntLittleEndian(deltaBuffer);
                        packObject.setSize(tar_size);

                        List<DeltaInstructionObject> deltaInstructionObjects = new ArrayList<>();
                        while (deltaBuffer.hasRemaining()) {
                            DeltaInstructionObject deltaInstructionObject = new DeltaInstructionObject();
                            int read = deltaBuffer.get();
                            int delta_type = read & PACK_MSB_MASK;

                            if (delta_type == 0) { // insert
                                //System.out.println("insert");
                                deltaInstructionObject.setDelta_type(DeltaType.INSERT);
                                int delta_size = read & PACK_7_BIT_MASK;
                                byte[] insert_data = new byte[delta_size];
                                deltaBuffer.get(insert_data);
                                deltaInstructionObject.setDelta_data(insert_data);
                            } else { // copy
                                //System.out.println("copy");
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
                        packObject.setDeltaInstructionObjects(deltaInstructionObjects);
                }

                packObjects.add(packObject);
            }

            // construct repo from packObjects

            // init git directory
            initCommand(dir);

            // write undeltified objects first
            for (PackObject object: packObjects) {
                ObjectType type = object.getType();
                if (type == ObjectType.DELTIFIED) continue;

                String hash = GitObject.writeRawObject(object.getContent(), type, Path.of(dir).toAbsolutePath());
                //out.println("wrote hash: " + hash);
            }

            // construct deltified objects
            for (PackObject object: packObjects) {
                ObjectType type = object.getType();
                if (type != ObjectType.DELTIFIED) continue;

                String baseHash = object.getBaseHash();
                //out.println("baseHash: " + baseHash);
                RawObject baseObject = RawObject.fromHash(baseHash, Path.of(dir).toAbsolutePath());

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

                String hash = GitObject.writeRawObject(content, baseObject.getType(), Path.of(dir).toAbsolutePath());
                //out.println("wrote after delta: " + hash);
            }

            File[] files = new File(Path.of(dir).toAbsolutePath().toString()).listFiles();
            //out.println("dir: " + Path.of(dir).toAbsolutePath());

            // build head commit
            Commit commit = (Commit) GitObject.fromHash(head_map.get("head_hash"), Path.of(dir).toAbsolutePath());
            //out.println("head commit:");
            //out.println(commit);
            //out.println("commit treehash: " + commit.getTree_hash());
            Tree tree = (Tree) GitObject.fromHash(commit.getTree_hash(), Path.of(dir).toAbsolutePath());

            checkOut(tree, Path.of(dir).toAbsolutePath());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // The Content-Type MUST be application/x-$servicename-advertisement. Clients SHOULD fall back to the dumb protocol if another content type is returned.
        // Clients MUST NOT continue if they do not support the dumb protocol.
        // Clients MUST validate the first five bytes of the response entity matches the regex ^[0-9a-f]{4}#. If this test fails, clients MUST NOT continue.
        // Clients MUST parse the entire response as a sequence of pkt-line records.

        // Clients MUST verify the first pkt-line is # service=$servicename. Clients MUST ignore an LF at the end of the line.
    }

    private static void checkOut(Tree tree) {
        checkOut(tree, Path.of("").toAbsolutePath());
    }

    private static void checkOut(Tree tree, Path root_dir) {
        for (TreeEntry entry: tree.getEntries()) {
            //System.out.println(entry.getPath() + ", " + entry.getMode());

            switch (entry.getMode()) {
                case TreeEntry.EntryMode.DIRECTORY:
                    //out.println("directory entry gethash: " + entry.getHash());
                    Tree subTree = (Tree) GitObject.fromHash(entry.getHash(), git_root_dir);
                    Path subRoot = root_dir.resolve(entry.getPath());
                    try {
                        Files.createDirectories(subRoot);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    //out.println("subRoot path: " + subRoot.toAbsolutePath());
                    checkOut(subTree, subRoot);
                    break;

                case TreeEntry.EntryMode.REGULAR_EXECUTABLE:
                case TreeEntry.EntryMode.REGULAR_NON_EXECUTABLE:
                    //out.println("blob entry gethash: " + entry.getHash() + ", path: " + entry.getPath() + ", mode: " + entry.getMode());
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

    private static int getSizeFromDelta(ByteBuffer deltaBuffer) {
        int val = 0;
        int shift = 0;

        int continue_read = 1;
        while (continue_read > 0) {
            int first = Byte.toUnsignedInt(pack.get());
            //object_size = (object_size << 7) + (first & PACK_7_BIT_MASK);
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

    private static int HexByteToLen(byte[] length) {
        //return 4096 * length[0] + 256 * length[1] + 16 * length[2] + length[3];
        return Integer.parseInt(new String(length, StandardCharsets.US_ASCII), 16);
    }

    private static void commitTreeCommand(String tree_hash, String param_p, String parent_commit_hash, String param_m, String message) {
        final String author = "test author";
        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        Commit commit = new Commit(author, author, tree_hash, parent_commit_hash, zonedDateTime, zonedDateTime, message);

        //String hash = GitObject.writeToFile(commit);
        String hash = GitObject.writeToFile(commit, Path.of("").toAbsolutePath());
        System.out.print(hash);
    }

    private static void writeTreeCommand() {
        List entries = parseDirToEntries(Paths.get("").toAbsolutePath());
        Tree root = new Tree(entries);
        //String hash = GitObject.writeToFile(root);
        String hash = GitObject.writeToFile(root, Paths.get("").toAbsolutePath());
        System.out.print(hash);
    }

    private static List parseDirToEntries(Path path) {
        List<TreeEntry> entries = new ArrayList<>();
        File[] files = path.toFile().listFiles();

        for (File file: files) {
            if (file.getName().startsWith(".")) {
                continue;
            }
            if (file.isFile()) {
                TreeEntry.EntryMode mode = file.canExecute() ? TreeEntry.EntryMode.REGULAR_EXECUTABLE : TreeEntry.EntryMode.REGULAR_NON_EXECUTABLE;
                TreeEntry entry = new TreeEntry(mode, file.getName(), hashObjectCommand("-w", file.getAbsolutePath()));
                entries.add(entry);
            } else { // directory
                List subEntries = parseDirToEntries(file.toPath());
                Tree subTree = new Tree(subEntries);
                TreeEntry entry = new TreeEntry(TreeEntry.EntryMode.DIRECTORY, file.getName(), subTree.getHash());
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(TreeEntry::getPath));

        Tree root = new Tree(entries);
        GitObject.writeToFile(root);

        return entries;
    }

    private static void lsTreeCommand(String param, String hash) {
        // tree object: tree [content size]\0[Entries having references to other trees and blobs]
        // tree entry: [mode] [path] 0x00 [sha, 20 bytes]
        switch (param) {
            case "--name-only" -> {
                Tree tree = (Tree) GitObject.fromHash(hash);
                for (TreeEntry entry: tree.getEntries()) {
                    System.out.println(entry.getPath());
                }
            }
            default -> System.out.println("Not supported: " + param);
        }
    }

    private static String hashObjectCommand(String mode, String file) {
        // blob object format: blob space [length] 0x00 [content]
        switch (mode) {
            case "-w" -> {
                return GitObject.writeToFile(Blob.fromFile(Path.of(file)));
            }
            default -> System.out.println("Not supported: " + mode);
        }
        return "";
    }

    private static void catFileCommand(String mode, String hash) {
        // blob object format: blob space [length] 0x00 [content]
        // https://wyag.thb.lt/#objects
        switch (mode) {
            case "-p" -> {
                Blob blob = (Blob) GitObject.fromHash(hash);
                System.out.print(blob.getContent());
            }
            default -> System.out.println("Not supported: " + mode);
        }
    }

    private static void initCommand() {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");

        try {
            head.createNewFile();
            Files.write(head.toPath(), "ref: refs/heads/master\n".getBytes());
            System.out.println("Initialized git directory");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void initCommand(String parent_dir) {

        //final File root = new File(".git");
        final File root = new File(parent_dir);
        final File git_root = new File(root, ".git");
        git_root.mkdirs();
        //new File(root, ".git").mkdirs();
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
}
