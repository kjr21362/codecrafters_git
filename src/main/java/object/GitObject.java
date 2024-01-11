package object;

import com.google.common.primitives.Bytes;
import util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

public interface GitObject {
    static String writeToFile(GitObject object) {
        byte[] content = object.toBytes();
        int length = content.length;
        byte[] blob_bytes = Bytes.concat(object.getType().getBytes(), " ".getBytes(), Integer.toString(length).getBytes(), new byte[]{0}, content);

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
            //System.out.print(hash);
            return hash;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public byte[] toBytes();

    public String getType();

    default String getHash() {
        byte[] content = this.toBytes();
        int length = content.length;
        byte[] blob_bytes = Bytes.concat(this.getType().getBytes(), " ".getBytes(), Integer.toString(length).getBytes(), new byte[]{0}, content);
        return Util.BytesToHash(blob_bytes);
    }
}
