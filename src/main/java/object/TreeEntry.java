package object;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@AllArgsConstructor
public class TreeEntry {

    public enum EntryMode {
        DIRECTORY("40000"),
        REGULAR_NON_EXECUTABLE("100644"),
        REGULAR_NON_EXECUTABLE_GROUP_WRITABLE("100664"),
        REGULAR_EXECUTABLE("100755"),
        SYMBOLIC_LINK("120000"),
        GITLINK("160000");

        final String mode;

        EntryMode(String mode) {
            this.mode = mode;
        }

        static EntryMode fromString(String mode) {
            for (EntryMode m : values()) {
                if (Objects.equals(m.mode, mode)) {
                    return m;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return mode;
        }
    }

    @Getter
    @Setter
    private EntryMode mode;
    @Getter
    @Setter
    private String path;
    @Getter
    @Setter
    private String hash;

    public byte[] toBytes() {
        // hash should be stored in their binary form, not as hexadecimal strings.
        return Bytes.concat(mode.toString().getBytes(), " ".getBytes(), path.getBytes(), new byte[]{0}, BaseEncoding.base16().lowerCase().decode(hash));
    }
}
