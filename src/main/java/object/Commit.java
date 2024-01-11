package object;

import constants.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
public class Commit implements GitObject {

    @Getter
    @Setter
    private String author;
    @Getter
    @Setter
    private String committer;
    @Getter
    @Setter
    private String tree_hash;
    @Getter
    @Setter
    private String parent_commit_hash;
    @Getter
    @Setter
    private LocalDateTime timestamp;
    @Getter
    @Setter
    private String message;

    @Override
    public String toString() {
        return String.format("tree %s\nparent %s\nauthor %s %s\ncommitter %s %s\n\n%s\n",
                tree_hash, parent_commit_hash, author, timestamp.toString(), author, timestamp.toString(), message);
    }

    @Override
    public byte[] toBytes() {
        return toString().getBytes();
    }

    @Override
    public String getType() {
        return ObjectType.COMMIT.toString();
    }
}
