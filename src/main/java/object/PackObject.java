package object;

import constants.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
public class PackObject {
    @Getter
    @Setter
    ObjectType type; //
    @Getter
    @Setter
    byte[] content; // for undeltified object
    @Getter
    @Setter
    String baseHash; // for deltified object
    @Getter
    @Setter
    int size;
    @Getter
    @Setter
    List<DeltaInstructionObject> deltaInstructionObjects; // for deltified object
}
