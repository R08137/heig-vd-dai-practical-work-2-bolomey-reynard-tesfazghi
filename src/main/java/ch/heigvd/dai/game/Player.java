package ch.heigvd.dai.game;

import java.util.ArrayList;

public class Player {
    private int id;
    private DeckOfCards deckOfCards;

    public Player (int id, int ... nbCards) {
        this.id = id;
        this.deckOfCards = new DeckOfCards(nbCards);
    }

    public int getId(){
        return this.id;
    }

    public ArrayList<Card> getCardsValue(){
        return deckOfCards.getCards();
    }

    public DeckOfCards getDeckOfCards(){return this.deckOfCards;}

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("Player " + id + " :\n");
        sb.append(deckOfCards.toString());
        return sb.toString();
    }

    public Card playLowestCard() {
        return deckOfCards.playFirstCard();
    }

    public boolean hasCards() {
        return !deckOfCards.isEmpty();
    }

}

