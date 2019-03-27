package eznetworking.server;

public enum PowerLevel {

    CUSTOM(0), LOW(1), MEDIUM(2), HIGH(3), ADMINISTRATOR(4);

    private final int levelValue;

    private PowerLevel(int levelValue) {
        this.levelValue = levelValue;
    }

    public int getLevelValue() {
        return levelValue;
    }
}
