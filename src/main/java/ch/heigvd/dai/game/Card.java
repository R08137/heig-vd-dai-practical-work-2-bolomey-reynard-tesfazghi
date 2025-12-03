package ch.heigvd.dai.game;

/**
 * Represents a single card in the game.
 * <p>
 * A card has an immutable integer value. Values in the main deck
 * normally range from 1 to 100. A value of 0 is used as a special
 * placeholder in some contexts (e.g., when no card has been played yet).
 */
public class Card implements Comparable<Card> {
    private final int value;

    /**
     * Creates a card with the given value.
     *
     * @param value the value of the card
     */
    public Card(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value of the card as a primitive.
     *
     * @return the card value
     */
    public int getValue() {
        return value;
    }

    /**
     * Compares this card with another card based on their values.
     *
     * @param o the other card to compare to
     * @return a negative integer, zero, or a positive integer as this card's value
     *         is less than, equal to, or greater than the specified card's value
     * @throws NullPointerException if {@code o} is {@code null}
     */
    @Override
    public int compareTo(Card o) {
        return Integer.compare(this.value, o.value);
    }

    /**
     * Returns a string representation of the card as an ASCII-art style box
     * containing its value. A value of 0 is displayed as an empty card.
     *
     * @return an ASCII-art representation of the card
     */
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
