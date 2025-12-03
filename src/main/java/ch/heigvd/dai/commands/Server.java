package ch.heigvd.dai.commands;

import java.io.*;
import java.util.List;
import java.util.Base64;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ch.heigvd.dai.game.Game;
import picocli.CommandLine;

/**
 * Server command-line entry point for the Mind network game.
 * <p>
 * The server manages a global lobby of players, dispatches commands,
 * maintains game state, and broadcasts lobby/game updates via a secondary
 * socket connection for each player.
 */
@CommandLine.Command(name = "server", description = "Start the server part of the network game.")
public class Server implements Callable<Integer> {

    private static final int MAX_PLAYERS = 10;

    /**
     * Global lobby of connected players.
     */
    private static final List<PlayerSession> players = new CopyOnWriteArrayList<>();
    private final AtomicInteger playerId = new AtomicInteger(1);
    private static boolean gameStarted = false; // tells if game is ongoing until round depleted or reset
    private boolean inPostVictoryPhase = false; // For broadcast and votes
    private boolean inPostDefeatPhase = false;
    private int difficulty = 0;
    private Game theMind = null;

    private final Map<PlayerSession, Integer> playerIndexInGame = new ConcurrentHashMap<>();


    /**
     * Represents a player session on the server, including command and broadcast sockets
     * and lobby/game state flags.
     */
    private static class PlayerSession {
        final int id;
        final Socket commandSocket;      // main command connection
        Socket broadcastSocket;          // second socket for broadcasts

        String name;
        boolean ready;
        boolean reset;

        /**
         * Creates a new player session.
         *
         * @param id            unique identifier of the player
         * @param commandSocket command socket used by the player
         * @param name          initial player name
         */
        PlayerSession(int id, Socket commandSocket, String name) {
            this.id = id;
            this.commandSocket = commandSocket;
            this.name = name;
            this.ready = false;
            this.reset = false;
            this.broadcastSocket = null; // will be attached later
        }
    }

    private PlayerSession lastPlay = null;

    /**
     * Resets the game state and lobby flags after a defeat or majority reset vote.
     * <p>
     * This method:
     * <ul>
     *     <li>Clears ready/reset flags for all players</li>
     *     <li>Clears the current {@link Game} instance</li>
     *     <li>Resets post-victory and post-defeat phases</li>
     *     <li>Resets difficulty to zero</li>
     *     <li>Broadcasts a fresh lobby state</li>
     * </ul>
     */
    private void gameReset() {
        for (PlayerSession p : players) {
            p.ready = false;
            p.reset = false;
        }
        theMind = null;
        gameStarted = false;
        inPostVictoryPhase = false;
        inPostDefeatPhase = false;
        lastPlay = null;
        difficulty = 0;

        playerIndexInGame.clear(); // <- add this

        sendLobbyState();
    }


    private static int foundReset;
    private static int foundReady;

    /**
     * Counts how many players have voted for reset and stores the result in {@code foundReset}.
     */
    private void findResetVote() {
        foundReset = 0;
        for (PlayerSession p : players) {
            if (p.reset) {
                foundReset += 1;
            }
        }
    }

    /**
     * Counts how many players are ready and stores the result in {@code foundReady}.
     */
    private void findReadyVote() {
        foundReady = 0;
        for (PlayerSession p : players) {
            if (p.ready) {
                foundReady += 1;
            }
        }
    }

    private static final int LOBBY_RETURN_DELAY_SECONDS = 5;
    /**
     * End-of-line sequence used in protocol messages.
     */
    public static String END_OF_LINE = "\n";

    private static final int THREAD_POOL_SIZE = 22;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    /**
     * Port to listen on for the command socket.
     */
    @CommandLine.Option(
            names = {"-p", "--port"},
            description = "Port to use (default: ${DEFAULT-VALUE}).",
            defaultValue = "6433")
    protected int port;

    /**
     * Logs a message to stdout with a time-of-day prefix and [SERVER] tag.
     *
     * @param msg the message to log
     */
    private void log(String msg) {
        String ts = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + ts + "] [SERVER] " + msg);
    }

    /**
     * Entry point for the Picocli command.
     * <p>
     * Creates the command and broadcast server sockets, spawns a thread to handle
     * broadcast connections, and then accepts command connections, delegating them
     * to {@link #ClientHandler(Socket)}.
     *
     * @return 0 on normal termination, non-zero on error
     */
    @Override
    public Integer call() {
        log("Starting server...");

        try (ServerSocket commandServerSocket = new ServerSocket(port);
             ServerSocket broadcastServerSocket = new ServerSocket(port + 1)) {

            log("Listening on port " + port + " (commands)");
            log("Listening on port " + (port + 1) + " (broadcasts)");

            // thread that accepts broadcast connections and binds them to PlayerSession.broadcastSocket
            Thread broadcastAcceptor = new Thread(() -> acceptBroadcastSockets(broadcastServerSocket));
            broadcastAcceptor.setDaemon(true);
            broadcastAcceptor.start();

            // main loop: command connections
            while (!commandServerSocket.isClosed()) {
                try {
                    Socket socket = commandServerSocket.accept();
                    executor.submit(() -> ClientHandler(socket));
                } catch (IOException e) {
                    log("IO exception on accept: " + e);
                    break;
                }
            }
        } catch (IOException e) {
            log("IO exception: " + e);
            return 1;
        } finally {
            executor.shutdown();
        }

        return 0;
    }

    /**
     * Handles the lifecycle of a single client connection on the command socket.
     * <p>
     * This includes:
     * <ul>
     *     <li>Assigning a player ID and adding the session to the lobby</li>
     *     <li>Processing incoming client commands</li>
     *     <li>Updating lobby and game state</li>
     *     <li>Removing the player on disconnect</li>
     * </ul>
     *
     * @param socket the client socket to handle
     */
    private void ClientHandler(Socket socket) {
        PlayerSession session = null;
        try (socket;
             Reader reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(reader);
             Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(writer)) {

            log("New client connected from "
                    + socket.getInetAddress().getHostAddress()
                    + ":"
                    + socket.getPort());

            // capacity check
            if (players.size() >= MAX_PLAYERS) {
                sendLine(out, ServerCommand.ERROR_LOBBY_FULL + " lobby_full");
                log("Lobby full. Closing client...");
                return;
            }

            // temporary session with default name; can be updated by NAME
            int id = playerId.getAndIncrement();
            String defaultName = "Player" + id;
            session = new PlayerSession(id, socket, defaultName);
            players.add(session);

            // send the assigned id to this client
            sendLine(out, ServerCommand.ID_ASSIGN + " " + id);

            // wait for ID_VALIDATE from client
            String ackLine = in.readLine();
            if (ackLine == null) {
                log("Client disconnected before ID_VALIDATE");
                players.remove(session);
                return;
            }
            String[] ackParts = ackLine.trim().split("\\s+");
            if (!ackParts[0].equalsIgnoreCase("ID_VALIDATE")) {
                log("Expected ID_VALIDATE, got: " + ackLine);
                players.remove(session);
                return;
            }

            // notify client connection
            sendLobbyState();
            String line;
            while ((line = in.readLine()) != null) {

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+", 2); // Splitter for inputs
                String keyword = parts[0].toUpperCase();
                ClientCommand command = null;

                try {
                    command = ClientCommand.valueOf(keyword);
                } catch (IllegalArgumentException e) {
                    // unknown command
                    sendLine(out, ServerCommand.WARNING_COMMAND_INVALID + " unknown_command");
                    continue;
                }

                String arg = (parts.length > 1) ? parts[1] : null;

                switch (command) {
                    case NAME -> { // "PlayerID" nickname server-side or client set
                        if (gameStarted) {
                            sendLine(out, ServerCommand.WARNING_GAME_IN_SESSION);
                            continue;
                        }
                        if (inPostDefeatPhase) {
                            sendLine(out, ServerCommand.WARNING_DEFEAT_WAIT_FOR_COUNTDOWN);
                            continue;
                        }
                        if (session.ready) {
                            sendLine(out, ServerCommand.WARNING_NAME_WITH_READY);
                            continue;
                        }
                        for (PlayerSession player : players) {
                            if (player.name.equals(arg)) {
                                sendLine(out, ServerCommand.WARNING_NAME_TAKEN);
                            }
                        }
                        session.name = arg;
                        sendLine(out, ServerCommand.NAME_VALIDATED + " " + arg);
                        log("Player" + session.id + " registered as " + arg + ".");
                        sendLobbyState();
                    }

                    case READY -> {
                        if (gameStarted && !inPostVictoryPhase) {
                            sendLine(out, ServerCommand.WARNING_GAME_IN_SESSION);
                            continue;
                        }
                        if (inPostDefeatPhase) {
                            sendLine(out, ServerCommand.WARNING_DEFEAT_WAIT_FOR_COUNTDOWN);
                            continue;
                        }
                        if (session.ready) {
                            sendLine(out, ServerCommand.WARNING_ALREADY_READY);
                            continue;
                        }
                        session.ready = true;
                        sendLine(out, ServerCommand.STATUS_UPDATE_READY + " Readied.");
                        log("Player" + session.id + "(" + session.name + ") ready.");

                        if (gameStarted && !inPostVictoryPhase) {
                            sendGameState(); // or nothing
                        } else if (inPostVictoryPhase) {
                            // After a victory: votes for next round
                            broadcastVictory();
                            handlePostVictoryVotes();
                        } else {
                            // Lobby before any game
                            sendLobbyState();
                            tryGameStartIfReady(difficulty);
                        }
                    }

                    case UNREADY -> {
                        if (gameStarted && !inPostVictoryPhase) {
                            sendLine(out, ServerCommand.WARNING_GAME_IN_SESSION);
                            continue;
                        }
                        if (inPostDefeatPhase) {
                            sendLine(out, ServerCommand.WARNING_DEFEAT_WAIT_FOR_COUNTDOWN);
                            continue;
                        }
                        if (!session.ready) {
                            sendLine(out, ServerCommand.WARNING_ALREADY_NOT_READY);
                            continue;
                        }
                        session.ready = false;
                        sendLine(out, ServerCommand.STATUS_UPDATE_UNREADY + " Unreadied.");
                        log("Player" + session.id + "(" + session.name + ") not ready.");
                        if (gameStarted && inPostVictoryPhase) {
                            broadcastVictory();
                        } else {
                            sendLobbyState();
                        }
                    }

                    case PLAY -> {
                        if (!gameStarted || theMind == null) {
                            sendLine(out, ServerCommand.WARNING_GAME_NOT_STARTED);
                            continue;
                        }
                        if (inPostDefeatPhase) {
                            sendLine(out, ServerCommand.WARNING_DEFEAT_WAIT_FOR_COUNTDOWN);
                            continue;
                        }
                        Integer gameIndex = playerIndexInGame.get(session);
                        if (gameIndex == null) {
                            // Session is not part of the current game (e.g., joined mid-round)
                            sendLine(out, ServerCommand.WARNING_GAME_IN_SESSION);
                            continue;
                        }

                        if (theMind.isPlayerDeckEmpty(gameIndex)) {
                            sendLine(out, ServerCommand.WARNING_DECK_EMPTY);
                            continue;
                        }
                        var playedCard = theMind.playLowestCardForPlayer(gameIndex).getValue();
                        sendLine(out, ServerCommand.CARD_PLAYED + " " + playedCard);
                        lastPlay = session;
                        if (validatePlay()) {
                            sendGameState();
                        }
                    }

                    case RESET -> {
                        if (!inPostVictoryPhase) {
                            sendLine(out, ServerCommand.WARNING_RESET_NOT_AVAILABLE);
                            continue;
                        }

                        session.reset = true;
                        sendLine(out, ServerCommand.RESET_ISSUED);
                        log("Player" + session.id + " voted for reset.");

                        broadcastVictory();
                        handlePostVictoryVotes();
                    }

                    case QUIT -> {
                        sendLine(out, ServerCommand.CLOSE_CONNECTION);
                        log("Client " + session.name + " requested quit. Closing connection.");
                        return; // cleanup in finally
                    }

                    case HELP -> {
                        // Not really a server command; if client sends it, treat as invalid
                        sendLine(out, ServerCommand.WARNING_COMMAND_INVALID + " help_not_a_server_command");
                    }
                }
            }
            log("Client disconnected: " + (session != null ? session.name : "unknown"));
        } catch (IOException e) {
            log("IO exception in client handler: " + e);
        } catch (Exception e) {
            log("Unexpected exception in client handler for player "
                    + (session != null ? session.id + " (" + session.name + ")" : "unknown") + ": " + e);
            e.printStackTrace();
        } finally {
            if (session != null) {
                players.remove(session);
                log("Removed player " + session.id + ": " + session.name);

                // If a player leaves during an active game, that’s an instant defeat.
                if (gameStarted && !inPostDefeatPhase && !inPostVictoryPhase) {
                    log("Player left during active game, triggering instant defeat.");
                    executeDefeat();
                } else {
                    // if outside of an active game, just refresh the lobby state.
                    sendLobbyState();
                }
            }
        }
    }


    /**
     * Accepts broadcast socket connections and attaches them to the corresponding
     * {@link PlayerSession} based on the client id
     *
     * @param broadcastServerSocket server socket listening for broadcast connections
     */
    private void acceptBroadcastSockets(ServerSocket broadcastServerSocket) {
        while (true) {
            try {
                Socket socket = broadcastServerSocket.accept();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String line = in.readLine();
                if (line == null) {
                    socket.close();
                    continue;
                }

                String[] parts = line.split("\\s+");
                if (parts.length < 2) {
                    socket.close();
                    continue;
                }

                int id;
                try {
                    id = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    socket.close();
                    continue;
                }

                PlayerSession target = null;
                for (PlayerSession ps : players) {
                    if (ps.id == id) {
                        target = ps;
                        break;
                    }
                }

                if (target == null) {
                    socket.close();
                    continue;
                }

                target.broadcastSocket = socket;
                log("Broadcast socket attached to player "
                        + target.id + " (" + target.name + ")");
                sendLobbyState();

            } catch (IOException e) {
                log("IO exception in broadcast acceptor: " + e);
                break;
            }
        }
    }

    /**
     * Sends a single line of text to a client via the given writer, followed by
     * {@link #END_OF_LINE}, and flushes the writer.
     *
     * @param out  writer connected to a client socket
     * @param line content to send
     * @throws IOException if sending fails
     */
    private void sendLine(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write(END_OF_LINE);
        out.flush();
    }

    /**
     * Sends a single {@link ServerCommand} as a line of text to a client.
     *
     * @param out writer connected to a client socket
     * @param msg server command to send
     * @throws IOException if sending fails
     */
    private void sendLine(BufferedWriter out, ServerCommand msg) throws IOException {
        sendLine(out, msg.toString());
    }

    /**
     * Sends a broadcast line to a specific player on their broadcast socket.
     *
     * @param p   the target player session
     * @param msg the message to send
     */
    private void sendBroadcastLine(PlayerSession p, String msg) {
        if (p.broadcastSocket == null) return; // session broadcast socket non-existent
        try {
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(p.broadcastSocket.getOutputStream(), StandardCharsets.UTF_8));
            out.write(msg);
            out.write(END_OF_LINE);
            out.flush();
        } catch (IOException e) {
            log("Failed to send message to " + p.name + ": " + e);
        }
    }

    /**
     * Broadcasts a text message to all players as a {@link ServerCommand#GAME_STATE}
     * payload, Base64-encoded.
     *
     * @param text the textual payload to broadcast
     */
    private void broadcastInfoToAll(String text) {
        String encoded = Base64.getEncoder()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
        String msg = ServerCommand.GAME_STATE + " " + encoded;

        for (PlayerSession p : players) {
            sendBroadcastLine(p, msg);
        }
    }

    /**
     * Sends the current lobby state to all players who have a broadcast socket.
     * <p>
     * Each player receives a personalized summary including who they are,
     * how many players are connected, how many are ready, and the full player list.
     */
    private void sendLobbyState() {
        if (players.isEmpty()) {
            return;
        }

        // Count ready players
        int totalPlayers = players.size();
        findReadyVote();

        // Personalized message for each player
        for (PlayerSession target : players) {
            if (target.broadcastSocket == null) {
                continue; // no broadcast socket
            }

            StringBuilder sbState = new StringBuilder();
            sbState.append("=== Lobby ===\n")
                    .append("Players connected: ")
                    .append(totalPlayers)
                    .append("\n")
                    .append("Players ready: ")
                    .append(foundReady)
                    .append(" / ")
                    .append(totalPlayers)
                    .append("\n\n");

            sbState.append("You are ")
                    .append(target.name)
                    .append(".\n\n");

            sbState.append("Player list:\n");

            for (PlayerSession p : players) {
                sbState.append(" - ")
                        .append(p.name);
                if (p == target) {
                    sbState.append(" (you)");
                }
                if (p.ready) {
                    sbState.append(" [READY]");
                }
                sbState.append("\n");
            }

            String encoded = Base64.getEncoder()
                    .encodeToString(sbState.toString().getBytes(StandardCharsets.UTF_8));
            String msg = ServerCommand.GAME_STATE + " " + encoded;

            sendBroadcastLine(target, msg);
        }
    }

    /**
     * Sends the current game state to all players, including their own deck, the top card
     * of the stack, and a list of player names.
     * <p>
     * Messages are sent as {@link ServerCommand#GAME_STATE} payloads encoded in Base64.
     */
    private void sendGameState() {
        if (theMind == null) {
            return;
        }

        String playerList = "";
        for (PlayerSession p : players) {
            playerList = playerList.concat(p.name + " ");
        }

        for (PlayerSession p : players) {
            if (p.broadcastSocket == null) {
                continue; // player has not established their state socket yet
            }

            Integer gameIndex = playerIndexInGame.get(p);
            if (gameIndex == null) {
                // This player is not part of the current round (e.g., joined mid-game)-> skip
                continue;
            }

            StringBuilder sbState = new StringBuilder();
            sbState.append("Player list: ").append(playerList).append("\n");
            sbState.append("You are " + p.name + ".\n\n"
                    + "Current deck : \n" + theMind.getPlayerDeck(gameIndex).toString()
                    + "Card in play :\n" + theMind.getTopOfStack());
            if (lastPlay != null) {
                sbState.append("played by ").append(lastPlay.name).append(".");
            }
            String encoded = Base64.getEncoder()
                    .encodeToString(sbState.toString().getBytes(StandardCharsets.UTF_8));

            String msg = ServerCommand.GAME_STATE + " " + encoded;

            sendBroadcastLine(p, msg);
        }

    }

    /**
     * Validates the last card play according to game rules and handles victory/defeat.
     *
     * @return {@code true} if the game continues after this play;
     * {@code false} if the game has ended (victory or defeat)
     */
    private boolean validatePlay() { // returns true if game on and false if game stopped
        boolean isPlayedCardValid = theMind.validatePlayedSequence();
        boolean isGameFinished = theMind.isFinished();

        if (isPlayedCardValid && isGameFinished) { // Victory conditions are met!
            executeVictory();
            return false;
        } else if (isPlayedCardValid) {
            return true; // Game continues as is.
        } else { // Played card is invalid. Players are all doomed
            executeDefeat();
            return false;
        }
    }

    /**
     * Executes the victory sequence: enters post-victory phase, clears ready/reset flags,
     * and broadcasts the victory message and vote instructions.
     */
    private void executeVictory() {
        inPostVictoryPhase = true;

        for (PlayerSession p : players) {
            p.ready = false;
            p.reset = false;
        }

        broadcastVictory();
    }

    /**
     * Broadcasts the current victory screen to all players, including reset/ready counts
     * and voting instructions.
     */
    private void broadcastVictory() {
        for (PlayerSession p : players) {
            if (p.broadcastSocket == null) {
                continue; // player without broadcast socket
            }
            findResetVote();
            findReadyVote();
            StringBuilder sbState = new StringBuilder();
            sbState.append("Victory has been achieved !\n")
                    .append("To proceed to next round (1 added card), issue READY to ready yourself.\n")
                    .append("You can also issue RESET to vote to reset rounds and go back to main lobby.\n\n")
                    .append(foundReset)
                    .append(" of ").append(players.size()).append(" voted for reset.\n")
                    .append(foundReady).append(" players ready.");

            String encoded = Base64.getEncoder()
                    .encodeToString(sbState.toString().getBytes(StandardCharsets.UTF_8));

            String msg = ServerCommand.GAME_STATE + " " + encoded;

            sendBroadcastLine(p, msg);
        }
    }

    /**
     * Processes votes in the post-victory phase, checking whether a majority has voted
     * for reset or whether all players are ready to continue at a higher difficulty.
     */
    private void handlePostVictoryVotes() {
        if (!inPostVictoryPhase) {
            return; // only valid in post-victory state
        }

        int readyCount = 0;
        int resetCount = 0;
        for (PlayerSession p : players) {
            if (p.ready) readyCount++;
            if (p.reset) resetCount++;
        }

        // Majority reset?
        if (resetCount * 2 > players.size()) {
            executeMajorityResetWithCountdown();
            return;
        }


        // Everyone ready to continue?
        if (!players.isEmpty() && readyCount == players.size()) {
            inPostVictoryPhase = false;
            difficulty += 1;
            StringBuilder sbState = new StringBuilder();
            sbState.append("Everyone ready for next round. Added difficulty of ")
                    .append(difficulty)
                    .append(" cards in deck.");

            broadcastInfoToAll(sbState.toString());

            tryGameStartIfReady(difficulty);
        }
    }

    /**
     * Executes a majority-reset sequence: shows a countdown then returns to lobby.
     */
    private void executeMajorityResetWithCountdown() {
        inPostVictoryPhase = false;
        new Thread(this::runMajorityResetCountdown).start();
    }

    /**
     * Runs the majority-reset countdown, then resets the game/lobby.
     */
    private void runMajorityResetCountdown() {
        int remaining = LOBBY_RETURN_DELAY_SECONDS;

        while (remaining > 0) {
            broadcastResetCountdown(remaining);
            try {
                Thread.sleep(1000); // 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // abort countdown if interrupted
            }
            remaining--;
        }
        // When countdown reaches 0, actually reset the game and return to lobby
        gameReset();
    }

    /**
     * Broadcasts the reset countdown to all players after a majority reset vote.
     *
     * @param secondsRemaining number of seconds left in the countdown
     */
    private void broadcastResetCountdown(int secondsRemaining) {
        StringBuilder sbState = new StringBuilder();
        sbState.append("Majority voted for reset.\n")
                .append("Returning to lobby in ")
                .append(secondsRemaining)
                .append(" seconds...");

        broadcastInfoToAll(sbState.toString());
    }


    /**
     * Executes the defeat sequence: enters post-defeat phase, broadcasts defeat,
     * and starts a countdown before returning to the lobby.
     */
    private void executeDefeat() {
        inPostDefeatPhase = true;
        broadcastDefeat();
        new Thread(this::runDefeatCountdown).start();
    }

    /**
     * Runs the defeat countdown, periodically broadcasting the remaining time and
     * then resetting the game to the lobby when the countdown reaches zero.
     */
    private void runDefeatCountdown() {
        int remaining = LOBBY_RETURN_DELAY_SECONDS;

        while (remaining > 0) {
            broadcastDefeatCountdown(remaining);
            try {
                Thread.sleep(1000); // 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // abort countdown if interrupted
            }
            remaining--;
        }

        // When countdown reaches 0, actually reset the game and return to lobby
        gameReset();
    }

    /**
     * Broadcasts a defeat message to all players, indicating the player who caused
     * the defeat if known.
     */
    private void broadcastDefeat() {
        StringBuilder sbState = new StringBuilder();
        sbState.append("Defeat!\n");
        if (lastPlay != null) {
            sbState.append(lastPlay.name).append(" has doomed us all...\n\n");
        }

        broadcastInfoToAll(sbState.toString());

    }

    /**
     * Broadcasts the defeat countdown to all players, indicating how many seconds
     * remain before returning to the lobby.
     *
     * @param secondsRemaining number of seconds left in the countdown
     */
    private void broadcastDefeatCountdown(int secondsRemaining) {
        StringBuilder sbState = new StringBuilder();
        sbState.append("Defeat!\n");
        if (lastPlay != null) {
            sbState.append(lastPlay.name).append(" has doomed us all...\n");
        } else {
            sbState.append("Not enough players to keep on playing...\n");
        }
        sbState.append("Returning to lobby in ")
                .append(secondsRemaining)
                .append(" seconds...");

        broadcastInfoToAll(sbState.toString());
    }

    /**
     * Attempts to start a new game if all players are ready and there are enough players.
     * <p>
     * The number of cards per player is {@code 5 + difficulty}.
     *
     * @param difficulty current difficulty level, used to increase deck size
     */
    // Check if enough players are present and all are ready; if so, start round 0.
    private void tryGameStartIfReady(int difficulty) {
        if (players.isEmpty() || players.size() <= 1) {
            return;
        }
        findReadyVote();
        if (foundReady != players.size()) {
            return;
        }

        int playerCount = players.size();
        int cardsPerPlayer = 5 + difficulty;

        // Round cap: cannot deal more than 100 cards total.
        if (playerCount * cardsPerPlayer > 100) {
            StringBuilder sbState = new StringBuilder();
            sbState.append("Ultimate victory!\n")
                    .append("No more rounds can be generated with ")
                    .append(playerCount).append(" players and ")
                    .append(cardsPerPlayer).append(" cards per player (deck has 100 cards).\n")
                    .append("Returning to lobby...");

            broadcastInfoToAll(sbState.toString());
            log("Round cap reached (" + playerCount + " players, " + cardsPerPlayer
                    + " cards per player). Returning to lobby.");
            gameReset();
            return;
        }

        try {
            // Build mapping from PlayerSession to game index 0..N-1 to avoid incoherences
            playerIndexInGame.clear();
            int index = 0;
            for (PlayerSession p : players) {
                playerIndexInGame.put(p, index++);
            }

            theMind = new Game(playerCount, cardsPerPlayer);
            gameStarted = true;
            log("Game started with " + playerCount + " players.");

            sendGameState();

        } catch (Exception e) {
            log("Failed to start Game: " + e);
            e.printStackTrace();
        }
    }
}
