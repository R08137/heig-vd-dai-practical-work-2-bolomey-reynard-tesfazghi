package ch.heigvd.dai.game;

import java.util.ArrayList;
import java.util.TreeSet;

import static ch.heigvd.dai.game.DisplayCards.*;

public class DeckOfCards {
    TreeSet<Card> cards = new TreeSet<>();

    public DeckOfCards(int ... cards) {
        this.cards = new TreeSet<>(); // Card doit implémenter Comparable<Card> ou fournir un Comparator
        for (int cardValue : cards) {
            this.cards.add(new Card(cardValue));
        }
    }

    public ArrayList<Card> getCards(){
        return new ArrayList<>(cards);
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(printTopOfCards(cards));
        sb.append(printValuesOfCards(cards));
        sb.append(printBottomOfCards(cards));
        return sb.toString();
    }

    public Card playFirstCard() {
        if (cards.isEmpty()) return null;
        Card first = cards.first();
        cards.remove(first);
        return first;
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    // Remove player()
        // Vérifie qu'il reste au moins 2 joueurs
        // Retire ses cartes du set de la solution

}
