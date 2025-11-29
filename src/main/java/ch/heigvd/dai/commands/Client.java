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
                    String[] userInputParts = userInput.split(" ", 2);
                    ClientCommand message =
                            ClientCommand.valueOf(userInputParts[0].toUpperCase());

                    String request = null;

                    switch (message) {
                        case NAME -> {
                            String name = (userInputParts.length > 1) ? userInputParts[1] : RandomName();
                            request = ClientCommand.NAME + " " + name + END_OF_LINE;
                            //TODO No arg -> random name

                        }

                        case PLAY -> {
                            int number = Integer.parseInt(userInputParts[1]);
                            request = ClientCommand.PLAY + " " + number + END_OF_LINE;
                        }
                        case QUIT -> {
                            socket.close();
                            continue;
                        }
                    }

                    if (request != null) {
                        out.write(request);
                        out.flush();
                    }
                } catch (Exception e) {
                    System.out.println("Invalid command. Please try again.");
                    continue;
                }

                String serverResponse = in.readLine();

                if (serverResponse == null) {
                    socket.close();
                    continue;
                }

                String[] serverResponseParts = serverResponse.split(" ", 2);

                ServerCommand message = null;
                try {
                    message = ServerCommand.valueOf(serverResponseParts[0]);
                } catch (IllegalArgumentException e) {
                    // Unknown message, ignore
                }

                switch (message) {
                    case NAME_VALIDATED -> {
                        String playerName = (serverResponseParts.length > 1)
                                ? serverResponseParts[1]
                                : "(unknown)";
                        System.out.println("Welcome " + playerName + "\n enjoy your game");
                    }
                    case null, default ->
                            System.out.println("Invalid/unknown command sent by server, ignore.");
                }
            }

            System.out.println("[Client] Closing connection and quitting...");
        } catch (Exception e) {
            System.out.println("[Client] Exception: " + e);
            return 1;
        }

        return 0;
    }

    public static String RandomName() {
        String adjective = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        return adjective + noun + RANDOM.nextInt(100);
    }

    private static void help() {
        System.out.println("Usage:");
        System.out.println("  " + ClientCommand.NAME + " <your name> - Play the game with a name. Without name gives random name");
        System.out.println("  " + ClientCommand.PLAY + " <number> - Submit the card number you want to play.");
        System.out.println("  " + ClientCommand.QUIT + " - Quit the game and close the connection to the server.");
        // System.out.println("  " + ClientCommand.PLACEHOLDER + " - Display this placeholder message.");
    }
}
