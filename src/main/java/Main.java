import com.google.common.primitives.Bytes;
import object.Blob;
import object.Commit;
import object.GitObject;
import object.Tree;
import object.TreeEntry;
import util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static java.lang.System.exit;

public class Main {
    public static void main(String[] args) {

        final String command = args[0];

        switch (command) {
            case "init" -> {
                initCommand();
            }
            case "cat-file" -> {
                catFileCommand(args[1], args[2]);
            }
            case "hash-object" -> {
                System.out.print(hashObjectCommand(args[1], args[2]));
            }
            case "ls-tree" -> {
                lsTreeCommand(args[1], args[2]);
            }
            case "write-tree" -> {
                writeTreeCommand();
            }
            case "commit-tree" -> {
                commitTreeCommand(args[1], args[2], args[3], args[4], args[5]);
            }
            default -> System.out.println("Unknown command: " + command);
        }
    }

    private static void commitTreeCommand(String tree_hash, String param_p, String parent_commit_hash, String param_m, String message) {
        final String author = "test author";
        Commit commit = new Commit(author, author, tree_hash, parent_commit_hash, LocalDateTime.now(), message);

        String hash = GitObject.writeToFile(commit);
        System.out.print(hash);
    }

    private static void writeTreeCommand() {
        List entries = parseDirToEntries(Paths.get("").toAbsolutePath());
        Tree root = new Tree(entries);
        String hash = Util.WriteTreeToFile(root);
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
                byte[] bytes = subTree.serialize();
                byte[] blob_bytes = Bytes.concat("tree ".getBytes(), Integer.toString(bytes.length).getBytes(), new byte[]{0}, bytes);
                String hash = Util.BytesToHash(blob_bytes);
                TreeEntry entry = new TreeEntry(TreeEntry.EntryMode.DIRECTORY, file.getName(), hash);
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(TreeEntry::getPath));

        Tree root = new Tree(entries);
        Util.WriteTreeToFile(root);

        return entries;
    }

    private static void lsTreeCommand(String param, String tree_sha) {
        // tree object: tree [content size]\0[Entries having references to other trees and blobs]
        // tree entry: [mode] [path] 0x00 [sha, 20 bytes]
        switch (param) {
            case "--name-only" -> {
                String header_sha = tree_sha.substring(0, 2);
                String content_sha = tree_sha.substring(2);

                try (InputStream fileStream = new FileInputStream(".git/objects/" + header_sha + "/" + content_sha);
                     // to decompress the data
                     InflaterInputStream inflaterInputStream = new InflaterInputStream(fileStream)) {

                    byte[] data = inflaterInputStream.readAllBytes();
                    int first_delimeter_idx = Bytes.indexOf(data, (byte) ' ');
                    int second_delimeter_idx = Bytes.indexOf(data, (byte) 0x00);
                    String type = new String(data, 0, first_delimeter_idx);
                    int length = Integer.valueOf(new String(data, first_delimeter_idx + 1, second_delimeter_idx - first_delimeter_idx - 1));
                    String entry = new String(data, second_delimeter_idx + 1, data.length - (second_delimeter_idx + 1));
                    if (!"tree".equals(type)) {
                        System.out.println("Not a tree object: " + tree_sha);
                        exit(0);
                    }

                    // recursively parse the tree entries
                    // tree entry: [mode] [path] 0x00 [sha, 20 bytes]
                    byte[] entries = Arrays.copyOfRange(data, second_delimeter_idx + 1, data.length);
                    ArrayList names = new ArrayList<String>();
                    while (entries.length > 0) {
                        int space_idx = Bytes.indexOf(entries, (byte)' ');
                        int null_idx = Bytes.indexOf(entries, (byte) 0x00);
                        if (null_idx < 0) {
                            break;
                        }
                        names.add(new String(Arrays.copyOfRange(entries, space_idx + 1, null_idx)));
                        entries = Arrays.copyOfRange(entries, null_idx + 21, entries.length);
                    }
                    names.sort((Comparator<String>) (o, t1) -> o.compareToIgnoreCase(t1));
                    for (Object name: names) {
                        System.out.println(name);
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
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
}
