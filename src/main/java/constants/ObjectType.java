package constants;

public enum ObjectType {
    DELTIFIED("deltified"),
    COMMIT("commit"),
    TREE("tree"),
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
