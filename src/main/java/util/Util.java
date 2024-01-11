package util;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;
import object.Tree;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

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

    public static String WriteTreeToFile(Tree root) {
        byte[] bytes = root.serialize();
        byte[] blob_bytes = Bytes.concat("tree ".getBytes(), Integer.toString(bytes.length).getBytes(), new byte[]{0}, bytes);

        String hash = Util.BytesToHash(blob_bytes);

        File blob_file = new File(".git/objects/" + hash.substring(0, 2) + "/" + hash.substring(2));
        try {
            com.google.common.io.Files.createParentDirs(blob_file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // BE aware - not closing the output streams properly would cause incorrect content
        // written to file (should close deflaterOutputStream first, then FileOutputStream)
        try (OutputStream outputStream = new FileOutputStream(blob_file);
             DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(outputStream)) {
            deflaterOutputStream.write(blob_bytes);
            return hash;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
