package constants;

public enum ObjectType {
    COMMIT("commit");

    private final String type;

    ObjectType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type;
    }
}
