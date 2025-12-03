package ch.heigvd.dai.commands;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Callable;

import picocli.CommandLine;

/**
 * Client command-line entry point for the Mind network game.
 * <p>
 * This class connects to a remote server, performs the initial handshake
 * to obtain a player id, opens a second socket for lobby/game state updates,
 * and then runs a text-based UI to send user commands and display server
 * responses.
 */
@CommandLine.Command(name = "client", description = "Start the client part of the network game.")
public class Client implements Callable<Integer> {

    // Enum for random name prefix
    private static final String[] ADJECTIVES = {
            "Brave", "Silent", "Happy", "Clever", "Swift",
            "Mighty", "Lucky", "Golden", "Wild", "Gentle",
            "Fierce", "Calm", "Bold", "Bright", "Rapid"
    };

    // Enum for random name
    private static final String[] NOUNS = {
            "Tiger", "Wolf", "Eagle", "Lion", "Fox",
            "Bear", "Hawk", "Panther", "Shark", "Falcon",
            "Dragon", "Otter", "Raven", "Stag", "Cobra"
    };

    // Random number for random name suffix
    private static final Random RANDOM = new Random();

    /**
     * Full string for available commands
     */
    private static final String HELP_TEXT =
            "[INFO] Available commands: NAME | NAME <your name> | READY | UNREADY | PLAY | RESET | QUIT | HELP\n";

    /**
     * End-of-line sequence used for protocol messages.
     */
    public static String END_OF_LINE = "\n";

    /**
     * Server host to connect to.
     */
    @CommandLine.Option(
            names = {"-H", "--host"},
            description = "Host to connect to.",
            required = true)
    protected String host;

    /**
     * Server port for the command socket.
     */
    @CommandLine.Option(
            names = {"-p", "--port"},
            description = "Port to use (default: ${DEFAULT-VALUE}).",
            defaultValue = "6433")
    protected int port;

    /**
     * Entry point for the Picocli command.
     * <p>
     * Establishes the command and state sockets, performs handshake,
     * starts the UI, and manages communication with the server.
     *
     * @return 0 on normal termination, non-zero on error
     */
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
                System.out.println("[CLIENT] No valid id from server. Aborting.");
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
            tui.updateServerText(HELP_TEXT);
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

    /**
     * Handles a single response line from the server on the command socket
     * and updates the {@link ClientUI} accordingly.
     *
     * @param serverResponse the raw line received from the server; may be {@code null}
     * @param tui            the client UI to update with messages
     * @param socket         the command socket to close in case of fatal errors
     * @throws IOException if closing the socket fails
     */
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
                tui.updateServerText("[INFO] You are now " + playerName + ". Welcome.");
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
            case WARNING_GAME_IN_SESSION -> {
                tui.updateServerText("[WARNING] Cannot issue command while game is in session!");
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
            case WARNING_DEFEAT_WAIT_FOR_COUNTDOWN -> {
                tui.updateServerText("[WARNING] Currently returning to lobby. Wait for new session to issue commands.");
            }
            case ERROR_LOBBY_FULL -> {
                tui.updateServerText("[ERROR] Lobby is full. Closing connection.");
                try { socket.close(); } catch (IOException ignored) {}
                tui.close();
            }
            case ERROR_FATAL -> {
                tui.updateServerText("[ERROR] Server fatal error, closing connection.");
                try { socket.close(); } catch (IOException ignored) {}
                tui.close();
            }
            case CLOSE_CONNECTION -> {
                tui.updateServerText("[INFO] QUIT issued. Closing connection...");
                try { socket.close(); } catch (IOException ignored) {}
                tui.close();
            }

            default -> tui.updateServerText("Invalid/unknown command sent by server...");
        }
    }

    /**
     * Parses a user command entered in the UI, converts it into the appropriate
     * protocol command, sends it to the server, and handles the immediate response.
     *
     * @param userInput the raw input line from the user
     * @param out       writer to send commands to the server
     * @param in        reader for responses from the server
     * @param socket    command socket used to communicate with the server
     * @param tui       UI to display errors or informational messages
     * @throws IOException if sending or receiving data fails
     */
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
                request = ClientCommand.QUIT + END_OF_LINE;
            }
            case HELP -> {
                tui.updateServerText(HELP_TEXT);
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
                try {
                    socket.close();
                } catch (IOException ignored) {}
                tui.close();           // stop TUI, program will exit
                return;
            }

            handleServerResponse(serverResponse, tui, socket);
        }
    }

    /**
     * Listens on the state socket for encoded game or lobby state updates and
     * forwards them to the UI.
     *
     * @param stateIn reader connected to the server's broadcast socket
     * @param tui     UI to receive state updates
     */
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

    /**
     * Generates a random player name composed of an adjective, a noun,
     * and a random integer suffix according to the enums at the start of the class.
     *
     * @return a randomly generated player name
     */
    public static String RandomName() {
        String adjective = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        return adjective + noun + RANDOM.nextInt(100);
    }

}
