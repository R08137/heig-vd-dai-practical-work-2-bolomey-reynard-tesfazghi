package ch.heigvd.dai.game;

public class Card implements Comparable<Card> {
    private final int value;

    public Card(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public int getValueAsObject() {
        Integer integer = value;
        return integer;
    };

    @Override
    public int compareTo(Card o) {
        return Integer.compare(this.value, o.value);
    }

    public String toString() {
        String tempValue;
        if (this.value != 0) {
            tempValue = String.format("%3d", this.value);
        } else tempValue = "   ";

        StringBuilder sb = new StringBuilder();
        sb.append("╔═══╗\n");
        sb.append("║").append(tempValue).append("║\n");
        sb.append("╚═══╝\n");
        return sb.toString();
    }
}
