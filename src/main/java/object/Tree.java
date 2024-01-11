package object;

import com.google.common.primitives.Bytes;
import constants.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@AllArgsConstructor
public class Tree implements GitObject {

    @Getter
    private List<TreeEntry> entries;

    public static Tree fromBytes(byte[] entries) {
        List<TreeEntry> entryList = new ArrayList<>();

        while (entries.length > 0) {
            int space_idx = Bytes.indexOf(entries, (byte)' ');
            int null_idx = Bytes.indexOf(entries, (byte) 0x00);
            if (null_idx < 0) {
                break;
            }
            String mode = new String(Arrays.copyOfRange(entries, 0, space_idx));
            String name = new String(Arrays.copyOfRange(entries, space_idx + 1, null_idx));
            String entry_hash = new String(Arrays.copyOfRange(entries, null_idx + 1, null_idx + 21));
            entryList.add(new TreeEntry(TreeEntry.EntryMode.fromString(mode), name, entry_hash));
            entries = Arrays.copyOfRange(entries, null_idx + 21, entries.length);
        }

        entryList.sort(Comparator.comparing(TreeEntry::getPath));
        return new Tree(entryList);
    }

    @Override
    public byte[] toBytes() {
        byte[] bytes = new byte[0];

        for (TreeEntry entry: entries) {
            bytes = Bytes.concat(bytes, entry.toBytes());
        }

        return bytes;
    }

    @Override
    public String getType() {
        return ObjectType.TREE.toString();
    }
}
