package object;

import com.google.common.primitives.Bytes;
import constants.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.InflaterInputStream;

@AllArgsConstructor
public class Blob implements GitObject {

    @Getter
    private String content;

    @Override
    public byte[] toBytes() {
        return content.getBytes();
    }

    @Override
    public String getType() {
        return ObjectType.BLOB.toString();
    }

    public static Blob fromFile(Path path) {
        try (InputStream inputStream = new FileInputStream(path.toFile())) {
            byte[] content = inputStream.readAllBytes();
            return new Blob(new String(content));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
