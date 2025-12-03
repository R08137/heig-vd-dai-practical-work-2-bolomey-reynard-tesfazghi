package ch.heigvd.dai.game;

import java.util.ArrayList;

/**
 * Represents a player in the game.
 * <p>
 * A player has an identifier and a personal {@link DeckOfCards}.
 */
public class Player {
    private int id;
    private DeckOfCards deckOfCards;

    /**
     * Creates a new player with a given identifier and initial card values.
     *
     * @param id      the logical identifier of the player (for display/logging)
     * @param nbCards the values of the cards dealt to this player
     */
    public Player(int id, int... nbCards) {
        this.id = id;
        this.deckOfCards = new DeckOfCards(nbCards);
    }

    /**
     * Returns this player's identifier.
     *
     * @return the player id
     */
    public int getId() {
        return this.id;
    }

    /**
     * Returns the cards currently held by this player as a list.
     * <p>
     * The returned list is a copy; modifying it will not affect the player's deck.
     *
     * @return a list of cards currently in the player's deck
     */
    public ArrayList<Card> getCardsValue() {
        return deckOfCards.getCards();
    }

    /**
     * Returns the deck of cards held by this player.
     *
     * @return the player's {@link DeckOfCards}
     */
    public DeckOfCards getDeckOfCards() {
        return this.deckOfCards;
    }

    /**
     * Returns a textual representation of the player and their deck.
     *
     * @return a string describing the player and their cards
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Player " + id + " :\n");
        sb.append(deckOfCards.toString());
        return sb.toString();
    }

    /**
     * Plays (removes and returns) the lowest-value card from this player's deck.
     *
     * @return the lowest card, or {@code null} if the player has no cards
     */
    public Card playLowestCard() {
        return deckOfCards.playFirstCard();
    }

    /**
     * Indicates whether the player still has cards.
     *
     * @return {@code true} if the deck is not empty; {@code false} otherwise
     */
    public boolean hasCards() {
        return !deckOfCards.isEmpty();
    }

}
