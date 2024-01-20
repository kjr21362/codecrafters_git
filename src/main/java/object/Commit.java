package object;

import constants.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AllArgsConstructor
@NoArgsConstructor
public class Commit implements GitObject {

    public static final Pattern AUTHOR_PATTERN = Pattern.compile("^(.*?) <(.*?)> (\\d+) ((?:\\+|-)\\d+)$");
    
    @Getter
    @Setter
    private AuthorSignature author;
    @Getter
    @Setter
    private AuthorSignature committer;
    @Getter
    @Setter
    private String tree_hash;
    @Getter
    @Setter
    private String parent_commit_hash;
    @Getter
    @Setter
    private String message;

    @Override
    public String toString() {
        return String.format("tree %s\nparent %s\nauthor %s\ncommitter %s\n\n%s\n",
                tree_hash, parent_commit_hash, author, committer, message);
    }

    @Override
    public byte[] toBytes() {
        return toString().getBytes();
    }

    @Override
    public String getType() {
        return ObjectType.COMMIT.toString();
    }

    public static Commit fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Map<String, String> headers = new HashMap<>();
        StringBuffer stringBuffer = new StringBuffer();

        while (buffer.hasRemaining()) {
            int value = buffer.get();
            if (value == '\n') {
                if (stringBuffer.isEmpty()) {
                    break;
                }

                String[] parts = stringBuffer.toString().split(" ", 2);
                headers.put(parts[0], parts[1]);

                stringBuffer.setLength(0);
            } else {
                stringBuffer.append((char) value);
            }
        }

        byte[] message = new byte[buffer.remaining()];
        buffer.get(message);

        AuthorSignature author = parseAuthorSignature(headers.get("author"));
        AuthorSignature committer = parseAuthorSignature(headers.get("committer"));

        return new Commit(author, committer, headers.get("tree"), headers.get("parent"), new String(message));
    }

    private static AuthorSignature parseAuthorSignature(String author) {
        Matcher matcher = AUTHOR_PATTERN.matcher(author);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid author: " + author);
        }

        Instant instant = Instant.ofEpochSecond(Long.parseLong(matcher.group(3)));
        ZoneId zoneId = ZoneId.of(matcher.group(4));
        return new AuthorSignature(matcher.group(1), matcher.group(2), ZonedDateTime.ofInstant(instant, zoneId));
    }
}
