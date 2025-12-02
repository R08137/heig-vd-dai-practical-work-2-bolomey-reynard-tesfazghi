package ch.heigvd.dai.commands;

import java.awt.desktop.SystemSleepEvent;
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

import ch.heigvd.dai.game.Game;
import picocli.CommandLine;

@CommandLine.Command(name = "server", description = "Start the server part of the network game.")
public class Server implements Callable<Integer> {

    private static final int MAX_PLAYERS = 10;

    // global lobby
    private static final List<PlayerSession> players = new CopyOnWriteArrayList<>();
    private final AtomicInteger playerId = new AtomicInteger(1);
    private static boolean gameStarted = false; // tells if game is ongoing until round depleted or reset
    private boolean inPostVictoryPhase = false; // For broadcast and votes
    private int difficulty = 0;
    private Game theMind = null;

    // per-player connection/session to identify them for game instance and info
    private static class PlayerSession {
        final int id;
        final Socket commandSocket;      // main command connection
        Socket broadcastSocket;          // second socket for broadcasts

        String name;
        boolean ready;
        boolean reset;

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

    private void gameReset() {
        for (PlayerSession p : players) {
            p.ready = false;
            p.reset = false;
        }
        theMind = null;
        gameStarted = false;
        inPostVictoryPhase = false;
        lastPlay = null;
        difficulty = 0;
        broadcastLobby();
    }


    private static int foundReset;
    private static int foundReady;

    private void findResetVote() {
        foundReset = 0;
        for (PlayerSession p : players) {
            if (p.reset) {
                foundReset += 1;
            }
        }
    }

    private void findReadyVote() {
        foundReady = 0;
        for (PlayerSession p : players) {
            if (p.ready) {
                foundReady += 1;
            }
        }
    }

    private static final int LOBBY_RETURN_DELAY_SECONDS = 6;
    public static String END_OF_LINE = "\n";

    private static final int THREAD_POOL_SIZE = 22;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    @CommandLine.Option(
            names = {"-p", "--port"},
            description = "Port to use (default: ${DEFAULT-VALUE}).",
            defaultValue = "6433")
    protected int port;

    @Override
    public Integer call() {
        System.out.println("Starting server...");

        try (ServerSocket commandServerSocket = new ServerSocket(port);
             ServerSocket broadcastServerSocket = new ServerSocket(port + 1)) {

            System.out.println("[SERVER] Listening on port " + port + " (commands)");
            System.out.println("[SERVER] Listening on port " + (port + 1) + " (broadcasts)");

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
                    System.out.println("[SERVER] IO exception on accept: " + e);
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("[SERVER] IO exception: " + e);
            return 1;
        } finally {
            executor.shutdown();
        }

        return 0;
    }


    private void ClientHandler(Socket socket) {
        PlayerSession session = null;
        try (socket;
             Reader reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(reader);
             Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(writer)) {

            System.out.println(
                    "[SERVER] New client connected from "
                            + socket.getInetAddress().getHostAddress()
                            + ":"
                            + socket.getPort());

            // capacity check
            if (players.size() >= MAX_PLAYERS) {
                sendLine(out, ServerCommand.ERROR_LOBBY_FULL + " lobby_full");
                System.out.println("[SERVER] Lobby full. Closing client...");
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
                System.out.println("[SERVER] Client disconnected before ID_VALIDATE");
                players.remove(session);
                return;
            }
            String[] ackParts = ackLine.trim().split("\\s+");
            if (!ackParts[0].equalsIgnoreCase("ID_VALIDATE")) {
                System.out.println("[SERVER] Expected ID_VALIDATE, got: " + ackLine);
                players.remove(session);
                return;
            }

            // notify client connection
            broadcastLobby();

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
                            sendLine(out, ServerCommand.WARNING_NAME_MID_SESSION);
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
                        System.out.println("[SERVER] Player" + session.id + " registered as " + arg + ".");
                        broadcastLobby();
                    }

                    case READY -> {
                        if (session.ready) {
                            sendLine(out, ServerCommand.WARNING_ALREADY_READY);
                            continue;
                        }
                        session.ready = true;
                        sendLine(out, ServerCommand.STATUS_UPDATE_READY + " Readied.");
                        System.out.println("[SERVER] Player" + session.id + "(" + session.name + ") ready.");

                        if (gameStarted) {
                            // In-game READY – you might not want to do anything here,
                            // but definitely do NOT show victory
                            broadcastGameState(); // or nothing
                        } else if (inPostVictoryPhase) {
                            // After a victory: votes for next round
                            broadcastVictory();
                            handlePostVictoryVotes();
                        } else {
                            // Lobby before any game
                            broadcastLobby();
                            tryGameStartIfReady(difficulty);
                        }
                    }



                    case UNREADY -> {
                        if (!session.ready) {
                            sendLine(out, ServerCommand.WARNING_ALREADY_NOT_READY);
                            continue;
                        }
                        session.ready = false;
                        sendLine(out, ServerCommand.STATUS_UPDATE_UNREADY + " Unreadied.");
                        System.out.println("[SERVER] Player" + session.id + "(" + session.name + ") not ready.");
                        if (gameStarted) {
                            broadcastVictory();
                        } else {
                            broadcastLobby();
                        }
                    }

                    case PLAY -> {
                        if (!gameStarted || theMind == null) {
                            sendLine(out, ServerCommand.WARNING_GAME_NOT_STARTED);
                            continue;
                        }
                        if (theMind.isPlayerDeckEmpty(session.id - 1)) {
                            sendLine(out, ServerCommand.WARNING_DECK_EMPTY);
                            continue;
                        }
                        var playedCard = theMind.playLowestCardForPlayer(session.id - 1).getValue();
                        sendLine(out, ServerCommand.CARD_PLAYED + " " + playedCard);
                        lastPlay = session;
                        if (validatePlay()) {broadcastGameState();};
                    }

                    case RESET -> {
                        if (!inPostVictoryPhase) {
                            sendLine(out, ServerCommand.WARNING_RESET_NOT_AVAILABLE);
                            continue;
                        }

                        session.reset = true;
                        sendLine(out, ServerCommand.RESET_ISSUED);
                        System.out.println("[SERVER] Player" + session.id + " voted for reset.");

                        broadcastVictory();
                        handlePostVictoryVotes();
                    }



                    case QUIT -> {
                        sendLine(out, ServerCommand.CLOSE_CONNECTION);
                        System.out.println("[Server] Client " + session.name + " requested quit. Closing connection.");
                        return; // cleanup in finally
                    }

                    case HELP -> {
                        // Not really a server command; if client sends it, treat as invalid
                        sendLine(out, ServerCommand.WARNING_COMMAND_INVALID + " help_not_a_server_command");
                    }
                }
            }
            System.out.println("[SERVER] Client disconnected: " + (session != null ? session.name : "unknown"));
        } catch (IOException e) {
            System.out.println("[SERVER] IO exception in client handler: " + e);
        } catch (Exception e) {
            System.out.println("[SERVER] Unexpected exception in client handler for player "
                    + (session != null ? session.id + " (" + session.name + ")" : "unknown") + ": " + e);
            e.printStackTrace();
        } finally {
            if (session != null) {
                players.remove(session);
                System.out.println("[SERVER] Removed player " + session.id + ": " + session.name);
                broadcastLobby();
            }
        }
    }

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
                System.out.println("[SERVER] Broadcast socket attached to player "
                        + target.id + " (" + target.name + ")");

            } catch (IOException e) {
                System.out.println("[SERVER] IO exception in broadcast acceptor: " + e);
                break;
            }
        }
    }


    private void sendLine(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write(END_OF_LINE);
        out.flush();
    }

    private void sendLine(BufferedWriter out, ServerCommand msg) throws IOException {
        sendLine(out, msg.toString());
    }

    private void sendBroadcastLine(PlayerSession p, String msg) {
        if (p.broadcastSocket == null) return; // session broadcast socket non existent
        try {
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(p.broadcastSocket.getOutputStream(), StandardCharsets.UTF_8));
            out.write(msg);
            out.write(END_OF_LINE);
            out.flush();
        } catch (IOException e) {
            System.out.println("[SERVER] Failed to send message to " + p.name + ": " + e);
        }
    }


    private void broadcastLobby() {
        // TODO
    }

    private void broadcastGameState() {
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
            StringBuilder sbState = new StringBuilder();
            sbState.append("Player list: ").append(playerList).append("\n");
            sbState.append("You are " + p.name + ".\n\n"
                    + "Current deck : \n" + theMind.getPlayerDeck(p.id - 1).toString()
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

    private void executeVictory() {
        gameStarted = false;
        inPostVictoryPhase = true;

        for (PlayerSession p : players) {
            p.ready = false;
            p.reset = false;
        }

        broadcastVictory();
    }


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
        if (resetCount > players.size() / 2) {
            inPostVictoryPhase = false;
            gameReset();
            String msg = "Majority voted for reset. Returning to Lobby.";
            for (PlayerSession p : players) {
                sendBroadcastLine(p, msg);
            }
            return;
        }

        // Everyone ready to continue?
        if (!players.isEmpty() && readyCount == players.size()) {
            inPostVictoryPhase = false;
            difficulty += 1;
            String msg = "Everyone ready for next round. Added difficulty of " + difficulty + " cards in deck.";
            for (PlayerSession p : players) {
                sendBroadcastLine(p, msg);
            }
            tryGameStartIfReady(difficulty);
        }
    }




    private void executeDefeat() {
        gameStarted = false;
        inPostVictoryPhase = false;

        broadcastDefeat();
        new Thread(this::runDefeatCountdown).start();
    }


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



    private void broadcastDefeat() {
        StringBuilder sbState = new StringBuilder();
        sbState.append("Defeat has been achieved...!\n");
        if (lastPlay != null) {
            sbState.append(lastPlay.name).append(" has doomed us all...\n\n");
        }

        String encoded = Base64.getEncoder()
                .encodeToString(sbState.toString().getBytes(StandardCharsets.UTF_8));
        String msg = ServerCommand.GAME_STATE + " " + encoded;

        for (PlayerSession p : players) {
            if (p.broadcastSocket == null) {
                continue; // player has no broadcast socket
            }
            sendBroadcastLine(p, msg);
        }
    }

    private void broadcastDefeatCountdown(int secondsRemaining) {
        StringBuilder sbState = new StringBuilder();
        sbState.append("Defeat has been achieved...!\n");
        if (lastPlay != null) {
            sbState.append(lastPlay.name).append(" has doomed us all...\n");
        }
        sbState.append("Returning to lobby in ")
                .append(secondsRemaining)
                .append(" seconds...");

        String encoded = Base64.getEncoder()
                .encodeToString(sbState.toString().getBytes(StandardCharsets.UTF_8));
        String msg = ServerCommand.GAME_STATE + " " + encoded;

        for (PlayerSession p : players) {
            sendBroadcastLine(p, msg);
        }
    }




    // Check if enough players are present and all are ready; if so, start round 0.
    private void tryGameStartIfReady(int difficulty) {
        if (players.isEmpty() || players.size() <= 1) {
            return;
        }
        findReadyVote();
        if (foundReady != players.size()) {
            return;
        }

        try {
            theMind = new Game(players.size(), 5 + difficulty);
            gameStarted = true;
            System.out.println("[SERVER] Game started with " + players.size() + " players.");

            broadcastGameState();

        } catch (Exception e) {
            System.out.println("[SERVER] Failed to start Game: " + e);
            e.printStackTrace(); // TODO Notify all players
        }
    }
}

