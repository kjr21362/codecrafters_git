package constants;

public enum ObjectType {
    COMMIT("commit"),
    BLOB("blob");

    private final String type;

    ObjectType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type;
    }
}
