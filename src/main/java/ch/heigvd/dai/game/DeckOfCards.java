package ch.heigvd.dai.game;

import java.util.ArrayList;
import java.util.TreeSet;

import static ch.heigvd.dai.game.DisplayCards.*;

/**
 * Represents a player's deck of cards.
 * <p>
 * Internally, the deck is stored as a sorted {@link TreeSet} of {@link Card}s.
 * This ensures that retrieving and playing the lowest-value card is efficient.
 */
public class DeckOfCards {
    TreeSet<Card> cards = new TreeSet<>();

    /**
     * Creates a deck containing the given card values.
     * <p>
     * Each value passed is wrapped into a {@link Card} instance and inserted
     * into the internal sorted set.
     *
     * @param cards the values of the cards to add to the deck
     */
    public DeckOfCards(int... cards) {
        this.cards = new TreeSet<>();
        for (int cardValue : cards) {
            this.cards.add(new Card(cardValue));
        }
    }

    /**
     * Returns a list of all cards in the deck.
     * <p>
     * The returned list is a copy; modifying it will not affect the deck.
     *
     * @return a list containing all cards in the deck, sorted by value
     */
    public ArrayList<Card> getCards() {
        return new ArrayList<>(cards);
    }

    /**
     * Returns an ASCII-art representation of the whole deck, showing the
     * top border, values, and bottom border of all cards in order.
     *
     * @return a multi-line string representing the deck visually
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(printTopOfCards(cards));
        sb.append(printValuesOfCards(cards));
        sb.append(printBottomOfCards(cards));
        return sb.toString();
    }

    /**
     * Plays (removes and returns) the lowest-value card from the deck.
     *
     * @return the lowest card in the deck, or {@code null} if the deck is empty
     */
    public Card playFirstCard() {
        if (cards.isEmpty()) return null;
        Card first = cards.first();
        cards.remove(first);
        return first;
    }

    /**
     * Returns the number of cards currently in the deck.
     *
     * @return the size of the deck
     */
    public int size() {
        return cards.size();
    }

    /**
     * Indicates whether the deck is empty.
     *
     * @return {@code true} if the deck contains no cards; {@code false} otherwise
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }
}
