package object;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@AllArgsConstructor
@NoArgsConstructor
public class AuthorSignature {
    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private String email;
    @Getter
    @Setter
    private ZonedDateTime timestamp;

    @Override
    public String toString() {
        return String.format("%s %s %s", name, email, timestamp.toString());
    }
}
