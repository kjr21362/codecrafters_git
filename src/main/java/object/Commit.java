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
    private ZonedDateTime author_timestamp;
    @Getter
    @Setter
    private ZonedDateTime committer_timestamp;
    @Getter
    @Setter
    private String message;

    @Override
    public String toString() {
        return String.format("tree %s\nparent %s\nauthor %s %s\ncommitter %s %s\n\n%s\n",
                tree_hash, parent_commit_hash, author, author_timestamp.toString(), author, committer_timestamp.toString(), message);
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
        Commit commit = new Commit();
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
        String tree_hash = headers.get("tree");
        String parent_hash = headers.get("parent");

        Matcher matcher = AUTHOR_PATTERN.matcher(headers.get("author"));
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid author: " + headers.get("author"));
        }
        String author = matcher.group(1);
        String author_email = matcher.group(2);
        //LocalDateTime author_localDateTime = LocalDateTime.parse(matcher.group(3));
        Instant instant = Instant.ofEpochSecond(Long.parseLong(matcher.group(3)));
        ZoneId zoneId = ZoneId.of(matcher.group(4));
        ZonedDateTime author_zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);

        matcher = AUTHOR_PATTERN.matcher(headers.get("committer"));
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid committer: " + headers.get("committer"));
        }
        String committer = matcher.group(1);
        String committer_email = matcher.group(2);
        //LocalDateTime committer_localDateTime = LocalDateTime.parse(matcher.group(3));
        Instant committer_instant = Instant.ofEpochSecond(Long.parseLong(matcher.group(3)));
        ZoneId committer_zoneId = ZoneId.of(matcher.group(4));
        ZonedDateTime committer_zonedDateTime = ZonedDateTime.ofInstant(committer_instant, committer_zoneId);

        return new Commit(author + " " + author_email, committer + " " + committer_email, tree_hash, parent_hash, author_zonedDateTime, committer_zonedDateTime, new String(message));
    }
}
