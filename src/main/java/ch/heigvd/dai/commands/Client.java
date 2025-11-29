package ch.heigvd.dai.commands;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(name = "client", description = "Start the client part of the network game.")
public class Client implements Callable<Integer> {

    private static final String[] ADJECTIVES = {
            "Brave", "Silent", "Happy", "Clever", "Swift",
            "Mighty", "Lucky", "Golden", "Wild", "Gentle",
            "Fierce", "Calm", "Bold", "Bright", "Rapid"
    };

    private static final String[] NOUNS = {
            "Tiger", "Wolf", "Eagle", "Lion", "Fox",
            "Bear", "Hawk", "Panther", "Shark", "Falcon",
            "Dragon", "Otter", "Raven", "Stag", "Cobra"
    };

    private static final Random RANDOM = new Random();

    public static String END_OF_LINE = "\n";

    @CommandLine.Option(
            names = {"-H", "--host"},
            description = "Host to connect to.",
            required = true)
    protected String host;

    @CommandLine.Option(
            names = {"-p", "--port"},
            description = "Port to use (default: ${DEFAULT-VALUE}).",
            defaultValue = "6433")
    protected int port;

    @Override
    public Integer call() {

        try (Socket socket = new Socket(host, port);
             Reader reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(reader);
             Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(writer)) {

            System.out.println("[Client] Connected to " + host + ":" + port);
            System.out.println();

            help();

            while (!socket.isClosed()) {
                System.out.print("> ");

                Reader inputReader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
                BufferedReader bir = new BufferedReader(inputReader);
                String userInput = bir.readLine();

                if (userInput == null) {
                    socket.close();
                    break;
                }

                try {
                    String[] userInputParts = userInput.trim().split("\\s+", 2);
                    if (userInputParts[0].isEmpty()) {
                        continue;
                    }

                    ClientCommand cmd =
                            ClientCommand.valueOf(userInputParts[0].toUpperCase());

                    String request = null;

                    switch (cmd) {
                        case NAME -> {
                            // NAME or NAME <name>
                            String name = (userInputParts.length > 1) ? userInputParts[1].trim() : RandomName();
                            request = ClientCommand.NAME + " " + name + END_OF_LINE;
                        }

                        case READY -> {
                            request = ClientCommand.READY + END_OF_LINE;
                        }

                        case UNREADY -> {
                            request = ClientCommand.UNREADY + END_OF_LINE;
                        }

                        case PLAY -> {
                            try {
                                if (userInputParts.length < 2) {
                                    System.out.println("Usage: PLAY <card>");
                                    continue;
                                }
                                int number = Integer.parseInt(userInputParts[1].trim());
                                request = ClientCommand.PLAY + " " + number + END_OF_LINE;
                            } catch (NumberFormatException e) {
                                System.out.println("Number expected. Usage: PLAY <card>");
                                continue;
                            }
                        }

                        case NEXT_ROUND -> {
                            request = ClientCommand.NEXT_ROUND + END_OF_LINE;
                        }

                        case QUIT -> {
                            socket.close();
                            continue;
                        }

                        case HELP -> {
                            help();
                            continue;
                        }
                    }

                    if (request != null) {
                        out.write(request);
                        out.flush();
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("[ERROR] Invalid command. Type HELP for command list.");
                    continue;
                }

                String serverResponse = in.readLine();

//                if () { //TODO
//                    System.out.println("[ERROR] Server timeout. Closing connection.");
//                    socket.close();
//                    continue;
//                }

                String[] serverResponseParts = serverResponse.split(" ", 2);
                ServerCommand message = null;
                try {
                    message = ServerCommand.valueOf(serverResponseParts[0]);
                } catch (IllegalArgumentException e) {
                    System.out.println("[ERROR] Unknown server message: " + serverResponseParts[0]);
                }

                switch (message) {
                    case NAME_VALIDATED -> {
                        String playerName = serverResponseParts[1];
                        System.out.println("[INFO] Welcome " + playerName + ". Please ready yourself.");
                    }

                    case STATUS_UPDATE_READY -> {
                        System.out.println("[INFO] Successfully readied.");
                    }

                    case STATUS_UPDATE_UNREADY -> {
                        System.out.println("[INFO] Successfully unreadied.");
                    }

                    case GAME_STATE -> {
                        // TODO: update local view of round, hand, etc.
                    }

                    case VICTORY -> {
                        // TODO: react according to game spec (offer NEXT_ROUND, etc.)
                    }

                    case DEFEAT -> {
                        // TODO: go back to setup phase locally
                    }

                    case ERROR_LOBBY_FULL -> {
                        System.out.println("[ERROR] Lobby is full. Closing connection.");
                        socket.close();
                    }

                    case WARNING_GAME_NOT_STARTED -> {
                        System.out.println("[WARNING] Game not started yet. Wait for everyone to get ready.");
                    }

                    case WARNING_CANT_NAME_WHEN_READY -> {
                        System.out.println("[WARNING] Please unready yourself before renaming.");
                    }

                    case WARNING_NAME_TAKEN -> {
                        System.out.println("[WARNING] Name is already taken. Please choose another.");
                    }
                    case WARNING_COMMAND_INVALID -> {
                        System.out.println("[WARNING] Invalid command.");
                    }

                    case WARNING_CARD_NOMATCH -> {
                        System.out.println("[WARNING] You attempted to play a card you don't have. Please try again.");
                    }

                    case WARNING_CARD_COOLDOWN -> {
                        System.out.println("[WARNING] You must wait before playing another card.");
                    }

                    case ERROR_FATAL -> {
                        System.out.println("[ERROR] Server fatal error, Closing connection.");
                        socket.close();
                    }

                    case null, default -> System.out.println("Invalid/unknown command sent by server...");
                }
            }

            System.out.println("[CLIENT] Closing connection...");
        } catch (Exception e) {
            System.out.println("[CLIENT] Exception: " + e);
            return 1;
        }

        return 0;
    }

    public static String RandomName() {
        String adjective = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        return adjective + noun + RANDOM.nextInt(100);
    }

    private static void help() { // Mabye make a stategame static boolean and show different help message
        System.out.println("Usage:");
        System.out.println("  NAME [<your name>]  - Register your name. Without name lets server pick one.");
        System.out.println("  READY               - Mark yourself as ready in the lobby.");
        System.out.println("  UNREADY             - Mark yourself as not ready.");
        System.out.println("  PLAY <number>       - Play a card.");
        System.out.println("  NEXT_ROUND          - Request next round after victory.");
        System.out.println("  QUIT                - Quit the game and close the connection.");
        System.out.println("  HELP                - Display this help message.");
    }
}
