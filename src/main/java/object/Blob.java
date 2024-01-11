package object;

import constants.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;

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
            return fromBytes(inputStream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Blob fromBytes(byte[] content) {
        return new Blob(new String(content));
    }
}
