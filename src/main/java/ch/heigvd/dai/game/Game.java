package ch.heigvd.dai.game;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Game {
    private ArrayList<Player> players;
    private int nbCards;
    private ArrayList<Card> stackOfCards = new ArrayList<>(); // played cards
    private ArrayList<Card> remainingCards = new ArrayList<>(); // cards out of this game

    public Game(int nbPlayers, int nbCards) {
        if (nbPlayers < 1 || nbCards < 0) {
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
            players.add(new Player(i+1, hand));
        }

        System.out.println("Game created with " + nbPlayers + " players and " + nbCards + " cards per player.");
    }

    public boolean isPlayerDeckEmpty(int playerId) {
        Player targetPlayer = players.get(playerId);
        if(targetPlayer.hasCards()) { return false; }
            else return true;

    }
    // Player plays a card
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

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("Number of players : " + players.size() + "\n");
        for (Player p : players) {
            sb.append(p.toString()).append("\n");
        }
        sb.append("Played cards :\n");
        int index = 0;
        for (Card c : stackOfCards) {
            sb.append(String.format("%2d, ", c.getValue()));
            if (++index % 10 == 0) { sb.append("\n"); }
        }
        index = 0;
        sb.append("\n");
        sb.append("Remaining cards :\n");
        for (Card c : remainingCards) {
            sb.append(String.format("%2d, ", c.getValue()));
            if (++index % 10 == 0) { sb.append("\n"); }
        }
        return sb.toString();
    }

    public boolean isFinished() {
        for (Player p : players) {
            if (p.hasCards()) return false;
        }
        return true;
    }

    public boolean validatePlayedSequence() {
        // Played cards
        List<Card> playedSoFar = new ArrayList<>(stackOfCards);

        // Cards in hands of players
        List<Card> remainingInHands = new ArrayList<>();
        for (Player p : players) {
            remainingInHands.addAll(p.getCards());
        }

        // Combine cards played with those remaining in hands
        List<Card> combined = new ArrayList<>();
        combined.addAll(remainingInHands);
        combined.addAll(playedSoFar);
        Collections.sort(combined);

        // Verify order
        for (int i = 0; i < playedSoFar.size(); i++) {
            if (combined.get(i).getValue() != playedSoFar.get(i).getValue()) {
                System.out.println("Validation failed at played card: " + playedSoFar.get(i).getValue());
                return false;
            }
        }
        System.out.println("All played cards are in correct order.");
        return true;
    }

    public ArrayList<Player> getPlayers() {
        return players;
    }
}
