package object;

import constants.DeltaType;
import lombok.Getter;
import lombok.Setter;

public class DeltaInstructionObject {
    @Getter
    @Setter
    DeltaType delta_type; // copy or insert
    @Getter
    @Setter
    int delta_offset;
    @Getter
    @Setter
    int delta_size;
    @Getter
    @Setter
    byte[] delta_data;
}
