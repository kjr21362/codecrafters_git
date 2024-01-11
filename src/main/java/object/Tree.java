package object;

import com.google.common.primitives.Bytes;
import lombok.AllArgsConstructor;

import java.util.List;
@AllArgsConstructor
public class Tree {
    List<TreeEntry> entries;

    public byte[] serialize() {
        byte[] bytes = new byte[0];

        for (TreeEntry entry: entries) {
            bytes = Bytes.concat(bytes, entry.toBytes());
        }

        return bytes;
    }

    public void deserialize(byte[] bytes) {

    }
}
