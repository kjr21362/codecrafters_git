package object;

import com.google.common.primitives.Bytes;
import constants.ObjectType;
import util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

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

    static GitObject fromHash(String hash) {
        String header_sha = hash.substring(0, 2);
        String content_sha = hash.substring(2);

        try (InputStream fileStream = new FileInputStream(".git/objects/" + header_sha + "/" + content_sha);
             // to decompress the data
             InflaterInputStream inflaterInputStream = new InflaterInputStream(fileStream)) {

            byte[] data = inflaterInputStream.readAllBytes();
            int first_delimeter_idx = Bytes.indexOf(data, (byte) ' ');
            int second_delimeter_idx = Bytes.indexOf(data, (byte) 0x00);
            String type = new String(data, 0, first_delimeter_idx);
            int length = Integer.valueOf(new String(data, first_delimeter_idx + 1, second_delimeter_idx - first_delimeter_idx - 1));
            String content = new String(data, second_delimeter_idx + 1, length);

            switch (type) {
                case "blob" -> {
                    return new Blob(content);
                }
                default -> System.out.println("Not supported: " + type);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
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
