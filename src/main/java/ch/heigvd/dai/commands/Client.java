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

            // Handshake
            int playerId = -1;
            String firstLine = in.readLine();
            if (firstLine != null) {
                String[] parts = firstLine.split(" ", 2);
                try {
                    ServerCommand cmd = ServerCommand.valueOf(parts[0]);
                    if (cmd == ServerCommand.ID_ASSIGN && parts.length > 1) {
                        playerId = Integer.parseInt(parts[1].trim());
                        System.out.println("[CLIENT] Assigned id " + playerId);

                        out.write("ID_VALIDATE" + END_OF_LINE);
                        out.flush();
                    } else {
                        System.out.println("[CLIENT] Unexpected first server message: " + firstLine);
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("[CLIENT] Unknown first server message: " + firstLine);
                }
            }

            if (playerId < 0) {
                System.out.println("[Client] No valid id from server. Aborting.");
                return 1;
            }

            // Second socket for game and lobby state
            Socket stateSocket = new Socket(host, port + 1);

            BufferedWriter stateOut = new BufferedWriter(
                    new OutputStreamWriter(stateSocket.getOutputStream(), StandardCharsets.UTF_8));
            stateOut.write("ID " + playerId + END_OF_LINE);
            stateOut.flush();

            BufferedReader stateIn = new BufferedReader(
                    new InputStreamReader(stateSocket.getInputStream(), StandardCharsets.UTF_8));

            // Create TUI
            ClientUI tui = new ClientUI();
            tui.updateServerText("[INFO] Connected to " + host + ":" + port);
            tui.updateLobby("Waiting for lobby state...");

            // State listener thread: push lobby/game state into TUI
            Thread stateListener = new Thread(() -> listenToGameState(stateIn, tui));
            stateListener.setDaemon(true);
            stateListener.start();


            // Run TUI in current thread: when user hits ENTER, send to server
            tui.run(userInput -> {
                try {
                    handleUserCommand(userInput, out, in, socket, tui);
                } catch (IOException e) {
                    tui.updateServerText("[ERROR] Failed to send command: " + e.getMessage());
                    try { socket.close(); } catch (IOException ignored) {}
                }
            });


            // If TUI exits:
            System.out.println("[CLIENT] Closing connection...");
            socket.close();
            stateSocket.close();
        } catch (Exception e) {
            System.out.println("[CLIENT] Exception: " + e);
            return 1;
        }

        return 0;
    }


    private void handleServerResponse(String serverResponse, ClientUI tui, Socket socket) throws IOException {
        if (serverResponse == null) {
            tui.updateServerText("[ERROR] Distant connection closed unexpectedly.");
            try {
                socket.close();
            } catch (IOException ignored) {}
            return;
        }

        String[] serverResponseParts = serverResponse.split(" ", 2);
        ServerCommand message = null;
        try {
            message = ServerCommand.valueOf(serverResponseParts[0]);
        } catch (IllegalArgumentException e) {
            tui.updateServerText("[ERROR] Unknown server message: " + serverResponseParts[0]);
            return;
        }

        switch (message) {
            case NAME_VALIDATED -> {
                String playerName = serverResponseParts.length > 1 ? serverResponseParts[1] : "";
                tui.updateServerText("[INFO] Welcome " + playerName + ".");
            }
            case STATUS_UPDATE_READY -> {
                tui.updateServerText("[INFO] Successfully readied.");
            }
            case STATUS_UPDATE_UNREADY -> {
                tui.updateServerText("[INFO] Successfully unreadied.");
            }
            case GAME_STATE -> {
                // Useless with game_state?
            }
            case CARD_PLAYED -> {
                String playedCard = serverResponseParts.length > 1 ? serverResponseParts[1] : "?";
                tui.updateServerText("[INFO] Successfully played card " + playedCard);
            }

            case RESET_ISSUED -> {
                tui.updateServerText("[INFO] Successfully voted for reset.");
            }
            case WARNING_GAME_NOT_STARTED -> {
                tui.updateServerText("[WARNING] Cannot issue command while game not started.");
            }
            case WARNING_NAME_MID_SESSION -> {
                tui.updateServerText("[WARNING] Cannot change name while game is in session!");
            }
            case WARNING_NAME_WITH_READY -> {
                tui.updateServerText("[WARNING] Please unready yourself before renaming.");
            }
            case WARNING_NAME_TAKEN -> {
                tui.updateServerText("[WARNING] Name is already taken. Please choose another.");
            }
            case WARNING_ALREADY_READY -> {
                tui.updateServerText("[WARNING] You already are readied!");
            }
            case WARNING_ALREADY_NOT_READY -> {
                tui.updateServerText("[WARNING] You already are unreadied!");
            }
            case WARNING_COMMAND_INVALID -> {
                tui.updateServerText("[WARNING] Invalid command.");
            }
            case WARNING_CARD_NOMATCH -> {
                tui.updateServerText("[WARNING] You attempted to play a card you don't have. Please try again.");
            }
            case WARNING_CARD_SYNTAX -> {
                tui.updateServerText("[WARNING] Invalid card expression. Please try again");
            }
            case WARNING_CARD_COOLDOWN -> {
                tui.updateServerText("[WARNING] You must wait before playing another card.");
            }
            case WARNING_DECK_EMPTY -> {
                tui.updateServerText("[WARNING] No cards left!!!");
            }
            case WARNING_RESET_NOT_AVAILABLE -> {
                tui.updateServerText("[WARNING] Can't reset out of victory screen.");
            }
            case ERROR_LOBBY_FULL -> {
                tui.updateServerText("[ERROR] Lobby is full. Closing connection.");
                try { socket.close(); } catch (IOException ignored) {}
            }
            case ERROR_FATAL -> {
                tui.updateServerText("[ERROR] Server fatal error, Closing connection.");
                try { socket.close(); } catch (IOException ignored) {}
            }
            case CLOSE_CONNECTION -> {
                tui.updateServerText("[INFO] QUIT issued. Closing connection...");
                tui.close();
            }
            default -> tui.updateServerText("Invalid/unknown command sent by server...");
        }
    }

    private void handleUserCommand(String userInput,
                                   BufferedWriter out,
                                   BufferedReader in,
                                   Socket socket,
                                   ClientUI tui) throws IOException {

        String trimmed = userInput.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String[] userInputParts = trimmed.split("\\s+", 2);
        ClientCommand cmd;
        try {
            cmd = ClientCommand.valueOf(userInputParts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            tui.updateServerText("[ERROR] Invalid command. Type HELP for command list.");
            return;
        }

        String request = null;

        switch (cmd) {
            case NAME -> {
                String name = (userInputParts.length > 1)
                        ? userInputParts[1].trim()
                        : RandomName();
                request = ClientCommand.NAME + " " + name + END_OF_LINE;
            }
            case READY -> request = ClientCommand.READY + END_OF_LINE;
            case UNREADY -> request = ClientCommand.UNREADY + END_OF_LINE;
            case PLAY -> request = ClientCommand.PLAY + END_OF_LINE;
            case RESET -> request = ClientCommand.RESET + END_OF_LINE;
            case QUIT -> {
                socket.close();
                tui.updateServerText("[INFO] Connection closed.");
                return;
            }
            case HELP -> {
                // Show help in the TUI
                tui.updateServerText("Usage: NAME, READY, UNREADY, PLAY, NEXT_ROUND, QUIT, HELP");
                return;
            }
        }

        if (request != null) {
            out.write(request);
            out.flush();

            // Immediately read the response for THIS command
            String serverResponse = in.readLine();
            if (serverResponse == null) {
                tui.updateServerText("[ERROR] Distant connection closed unexpectedly.");
                socket.close();
                return;
            }

            handleServerResponse(serverResponse, tui, socket);
        }
    }

    private void listenToGameState(BufferedReader stateIn, ClientUI tui) {
        try {
            String line;
            while ((line = stateIn.readLine()) != null) {
                String[] parts = line.split(" ", 2);
                if (parts.length == 0) {
                    continue;
                }

                ServerCommand cmd;
                try {
                    cmd = ServerCommand.valueOf(parts[0]);
                } catch (IllegalArgumentException e) {
                    tui.updateLobby("[STATE] Unknown server message on state socket: " + parts[0]);
                    continue;
                }

                if (cmd == ServerCommand.GAME_STATE && parts.length > 1) {
                    String encoded = parts[1];
                    String gameState = new String(
                            java.util.Base64.getDecoder().decode(encoded),
                            StandardCharsets.UTF_8);

                    tui.updateLobby(gameState);
                } else {
                    tui.updateLobby("[STATE] " + line);
                }
            }
        } catch (IOException e) {
            tui.updateLobby("[STATE] State socket closed or error: " + e.getMessage());
        }
    }



    public static String RandomName() {
        String adjective = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        return adjective + noun + RANDOM.nextInt(100);
    }

    private static void help() { // Mabye make a stategame static boolean and show different help message
        System.out.println("Usage:");
        System.out.println("  NAME [<your name>]  - Register your name. No specification generates a random name.");
        System.out.println("  READY               - Mark yourself as ready in the lobby.");
        System.out.println("  UNREADY             - Mark yourself as not ready.");
        System.out.println("  PLAY <number>       - Play your lowest card in hand.");
        System.out.println("  NEXT_ROUND          - Request next round after victory.");
        System.out.println("  QUIT                - Quit the game and close the connection.");
        System.out.println("  HELP                - Display this help message.");
    }
}
