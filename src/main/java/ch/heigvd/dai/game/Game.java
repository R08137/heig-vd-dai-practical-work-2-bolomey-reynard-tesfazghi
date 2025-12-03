package ch.heigvd.dai.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Represents a single game of a simplifieed the Mind card game.
 * <p>
 * The game uses a deck of 100 unique cards with values from 1 to 100.
 * Each player is dealt {@code nbCards} random cards. Players play cards
 * in ascending order without communication. The class tracks:
 * <ul>
 *     <li>The players and their hands</li>
 *     <li>The stack of cards that have been played</li>
 *     <li>The remaining cards that are not in the current game (not dealt)</li>
 *     <li>The victory condition.</li>
 * </ul>
 */
public class Game {
    private ArrayList<Player> players;
    private int nbCards;
    private ArrayList<Card> stackOfCards = new ArrayList<>(); // played cards
    private ArrayList<Card> remainingCards = new ArrayList<>(); // cards out of this game

    /**
     * Constructor for a new game instance with the given number of players and cards per player.
     * <p>
     * A deck of 100 unique cards (values 1 to 100) is created. Cards are then randomly
     * distributed to each player. The remaining cards stay in {@code remainingCards}.
     *
     * @param nbPlayers the number of players; must be at least 2
     * @param nbCards   the number of cards dealt to each player; must be non-negative
     * @throws IllegalArgumentException if {@code nbPlayers < 2}, {@code nbCards < 1},
     *                                  or if {@code nbPlayers * nbCards > 100} (not enough cards for all players)
     */
    public Game(int nbPlayers, int nbCards) {
        if (nbPlayers < 2 || nbCards < 1) {
            throw new IllegalArgumentException("nbPlayers must be >=1 and nbCards must be >=0");
        }
        if (nbPlayers * nbCards > 100) {
            throw new IllegalArgumentException("Not enough cards: nbPlayers * nbCards must be <= 100");
        }

        this.nbCards = nbCards;
        this.players = new ArrayList<>();

        // Create the main deck of the game with 100 cards
        for (int v = 1; v <= 100; v++) {
            remainingCards.add(new Card(v));
        }

        // Distribute cards to players
        Random rnd = new Random();
        for (int i = 0; i < nbPlayers; i++) {
            int[] hand = new int[nbCards];
            for (int j = 0; j < nbCards; j++) {
                int idx = rnd.nextInt(remainingCards.size());
                Card drawn = remainingCards.remove(idx);
                hand[j] = drawn.getValue();
            }
            players.add(new Player(i + 1, hand));
        }

        System.out.println("Game created with " + nbPlayers + " players and " + nbCards + " cards per player.");
    }

    /**
     * Returns the deck of cards of the specified player.
     *
     * @param playerId the index of the player in the players list (0-based)
     * @return the {@link DeckOfCards} belonging to the player
     * @throws IndexOutOfBoundsException if {@code playerId} is out of range
     */
    public DeckOfCards getPlayerDeck(int playerId) {
        return players.get(playerId).getDeckOfCards();
    }

    /**
     * Checks whether the specified player's deck is empty.
     *
     * @param playerId the index of the player in the players list (0-based)
     * @return {@code true} if the player has no cards left; {@code false} otherwise
     * @throws IndexOutOfBoundsException if {@code playerId} is out of range
     */
    public boolean isPlayerDeckEmpty(int playerId) {
        Player targetPlayer = players.get(playerId);
        if (targetPlayer.hasCards()) {
            return false;
        } else return true;
    }

    /**
     * Makes the specified player play their lowest card.
     * <p>
     * The card is removed from the player's deck and added to the stack of played cards.
     *
     * @param playerId the index of the player in the players list (0-based)
     * @return the card that was played, or {@code null} if the player has no cards left
     * @throws IllegalArgumentException if {@code playerId} is out of bounds.
     */
    public Card playLowestCardForPlayer(int playerId) {
        if (playerId < 0 || playerId >= players.size()) {
            throw new IllegalArgumentException("playerId doesn't exists : " + playerId);
        }
        Player p = players.get(playerId);
        if (!p.hasCards()) {
            return null;
        }
        Card played = p.playLowestCard();
        if (played != null) {
            stackOfCards.add(played);
        }
        return played;
    }

    /**
     * Returns a textual representation of the current game state, including:
     * <ul>
     *     <li>Number of players</li>
     *     <li>Each player's hand</li>
     *     <li>The played cards</li>
     *     <li>The remaining cards (not dealt)</li>
     * </ul>
     *
     * @return a string describing the game state
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Number of players : " + players.size() + "\n");
        for (Player p : players) {
            sb.append(p.toString()).append("\n");
        }
        sb.append("Played cards :\n");
        int index = 0;
        for (Card c : stackOfCards) {
            sb.append(String.format("%2d, ", c.getValue()));
            if (++index % 10 == 0) {
                sb.append("\n");
            }
        }
        index = 0;
        sb.append("\n");
        sb.append("Remaining cards :\n");
        for (Card c : remainingCards) {
            sb.append(String.format("%2d, ", c.getValue()));
            if (++index % 10 == 0) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Returns the top card of the stack of played cards.
     *
     * @return the last card played, or a card with value {@code 0} if no card has been played yet
     */
    public Card getTopOfStack() {
        if (stackOfCards.isEmpty()) {
            return new Card(0);
        }
        return stackOfCards.get(stackOfCards.size() - 1);
    }

    /**
     * Indicates whether the game is finished.
     * <p>
     * The game is considered finished if all players have no cards left.
     *
     * @return {@code true} if all players have played all their cards; {@code false} otherwise
     */
    public boolean isFinished() {
        for (Player p : players) {
            if (p.hasCards()) return false;
        }
        return true;
    }

    /**
     * Validates that the sequence of played cards is consistent with the rules:
     * <p>
     * When combining all cards still in players' hands with all played cards,
     * the played cards must correspond to the lowest values of this combined set
     * in ascending order.
     *
     * @return {@code true} if the played sequence is valid; {@code false} otherwise
     */
    public boolean validatePlayedSequence() {
        // Played cards
        List<Card> playedSoFar = new ArrayList<>(stackOfCards);

        // Cards in hands of players
        List<Card> remainingInHands = new ArrayList<>();
        for (Player p : players) {
            remainingInHands.addAll(p.getCardsValue());
        }

        // Combine cards played with those remaining in hands
        List<Card> combined = new ArrayList<>();
        combined.addAll(remainingInHands);
        combined.addAll(playedSoFar);
        Collections.sort(combined);

        // Verify order
        for (int i = 0; i < playedSoFar.size(); i++) {
            if (combined.get(i).getValue() != playedSoFar.get(i).getValue()) {
                // System.out.println("Validation failed at played card: " + playedSoFar.get(i).getValue());
                return false;
            }
        }
        // System.out.println("All played cards are in correct order.");
        return true;
    }

    /**
     * Returns the list of players in the game.
     *
     * @return a list of {@link Player} instances participating in the game
     */
    public ArrayList<Player> getPlayers() {
        return players;
    }
}
