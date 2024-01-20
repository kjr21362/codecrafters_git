package util;

import com.google.common.io.BaseEncoding;
import object.Blob;
import object.GitObject;
import object.Tree;
import object.TreeEntry;

import java.io.File;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Util {

    public static String BytesToHash(byte[] bytes) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] sha = messageDigest.digest(bytes);
            return BaseEncoding.base16().lowerCase().encode(sha);
            //return HexFormat.of().formatHex(sha);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hashObjectCommand(String file) {
        // blob object format: blob space [length] 0x00 [content]
        return GitObject.writeToFile(Blob.fromFile(Path.of(file)));
    }

    public static List parseDirToEntries(Path path) {
        List<TreeEntry> entries = new ArrayList<>();
        File[] files = path.toFile().listFiles();

        for (File file: files) {
            if (file.getName().startsWith(".")) {
                continue;
            }
            if (file.isFile()) {
                TreeEntry.EntryMode mode = file.canExecute() ? TreeEntry.EntryMode.REGULAR_EXECUTABLE : TreeEntry.EntryMode.REGULAR_NON_EXECUTABLE;
                TreeEntry entry = new TreeEntry(mode, file.getName(), hashObjectCommand(file.getAbsolutePath()));
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
}
