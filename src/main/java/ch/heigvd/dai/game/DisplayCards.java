package ch.heigvd.dai.game;

/**
 * Utility class providing methods to render cards and decks as ASCII-art strings.
 * <p>
 * All methods are static and operate on iterable collections of {@link Card}.
 */
public class DisplayCards {

    /**
     * Builds the top border line for a sequence of cards.
     *
     * @param cards an iterable of cards to be represented
     * @return a string containing the top borders for all cards, followed by a newline
     */
    public static String printTopOfCards(Iterable<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            sb.append("╔═══╗");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Builds the middle line containing the values for a sequence of cards.
     *
     * @param cards an iterable of cards to be represented
     * @return a string containing the value lines for all cards, followed by a newline
     */
    public static String printValuesOfCards(Iterable<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            String v = String.format("%3d", c.getValue()); // largeur fixe 3
            sb.append("║").append(v).append("║");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Builds the bottom border line for a sequence of cards.
     *
     * @param cards an iterable of cards to be represented
     * @return a string containing the bottom borders for all cards, followed by a newline
     */
    public static String printBottomOfCards(Iterable<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            sb.append("╚═══╝");
        }
        sb.append("\n");
        return sb.toString();
    }
}
