package ch.heigvd.dai.game;

public class DisplayCards {

    public static String printTopOfCards(Iterable<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            sb.append("╔═══╗");
        }
        sb.append("\n");
        return sb.toString();
    }

    public static String printValuesOfCards(Iterable<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            String v = String.format("%3d", c.getValue()); // largeur fixe 3
            sb.append("║").append(v).append("║");
        }
        sb.append("\n");
        return sb.toString();
    }

    public static String printBottomOfCards(Iterable<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) {
            sb.append("╚═══╝");
        }
        sb.append("\n");
        return sb.toString();
    }
}
