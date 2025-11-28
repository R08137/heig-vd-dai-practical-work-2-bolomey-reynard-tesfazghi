package ch.heigvd.dai.game;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IllformedLocaleException;
import java.util.List;

public class MainTestGame {
    public static void main(String[] args) {

        int nbPlayer = 3;
        int nbCard = 5;

        Game game = new Game(nbPlayer, nbCard);
        int nbTurn = 0;

        System.out.println("========== Initial state ==========");
        System.out.println(game);
        System.out.println("Turn : " + nbTurn);

        int currentPlayer = 0;
        boolean valid = true;

        // Plays in order of player until game is finished or invalid
        while (valid && !game.isFinished()) {
            System.out.println("========== Player " + (currentPlayer + 1) + " plays ==========");
            Card lastPlayedCard = game.playLowestCardForPlayer(currentPlayer);
            if (lastPlayedCard != null) {
                System.out.println("Player " + (currentPlayer + 1) + " plays : " + lastPlayedCard.getValue());
            } else {
                System.out.println("Player " + (currentPlayer + 1) + " has no cards.");
            }
            System.out.println(game);

            System.out.println("Turn : " + ++nbTurn);

            // Validation
            valid = game.validatePlayedSequence();
            if (!valid) {
                System.out.println("Validation failed !");
                break;
            }


            // Next player's turn
            currentPlayer = (currentPlayer + 1) % nbPlayer;
        }

        if (game.isFinished() && valid) {
            System.out.println("Game finished successfully. All played cards were in correct order.");
        }
    }

}
