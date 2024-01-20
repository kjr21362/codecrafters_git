package object;

import com.google.common.primitives.Bytes;
import constants.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.zip.InflaterInputStream;

@AllArgsConstructor
@NoArgsConstructor
public class RawObject {
    @Getter
    @Setter
    ObjectType type;
    @Getter
    @Setter
    byte[] content;

    public static RawObject fromHash(String hash, Path root_dir) {
        String header_sha = hash.substring(0, 2);
        String content_sha = hash.substring(2);

        try (InputStream fileStream = new FileInputStream(root_dir.toString() + "/.git/objects/" + header_sha + "/" + content_sha);
             // to decompress the data
             InflaterInputStream inflaterInputStream = new InflaterInputStream(fileStream)) {

            byte[] data = inflaterInputStream.readAllBytes();
            int first_delimeter_idx = Bytes.indexOf(data, (byte) ' ');
            int second_delimeter_idx = Bytes.indexOf(data, (byte) 0x00);
            String type = new String(data, 0, first_delimeter_idx);
            int length = Integer.valueOf(new String(data, first_delimeter_idx + 1, second_delimeter_idx - first_delimeter_idx - 1));
            byte[] content = Arrays.copyOfRange(data, second_delimeter_idx + 1, second_delimeter_idx + 1 + length);

            switch (type) {
                case "blob" -> {
                    return new RawObject(ObjectType.BLOB, content);
                }
                case "tree" -> {
                    return new RawObject(ObjectType.TREE, content);
                }
                case "commit" -> {
                    return new RawObject(ObjectType.COMMIT, content);
                }
                default -> System.out.println("Not supported: " + type);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
