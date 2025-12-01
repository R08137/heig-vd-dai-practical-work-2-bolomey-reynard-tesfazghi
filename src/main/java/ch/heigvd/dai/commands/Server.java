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

import ch.heigvd.dai.game.Game;
import picocli.CommandLine;

@CommandLine.Command(name = "server", description = "Start the server part of the network game.")
public class Server implements Callable<Integer> {

    private static final int MAX_PLAYERS = 10;

    // global lobby
    private static final List<PlayerSession> players = new CopyOnWriteArrayList<>();
    private final AtomicInteger playerId = new AtomicInteger(1);
    private static boolean gameStarted = false;
    Game theMind = null;

    // per-player connection/session to identify them for game instance and info
    private static class PlayerSession {
        final int id;
        final Socket commandSocket;      // main command connection
        Socket broadcastSocket;          // second socket for broadcasts

        String name;
        boolean ready;

        PlayerSession(int id, Socket commandSocket, String name) {
            this.id = id;
            this.commandSocket = commandSocket;
            this.name = name;
            this.ready = false;
            this.broadcastSocket = null; // will be attached later
        }
    }

    private PlayerSession lastPlay = null;


    public static String END_OF_LINE = "\n";

    private static final int THREAD_POOL_SIZE = 10;
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
                        session.ready = true;
                        sendLine(out, ServerCommand.STATUS_UPDATE_READY + " Readied.");
                        System.out.println("[SERVER] Player" + session.id + "(" + session.name + ") ready.");
                        broadcastLobby();
                        tryGameStartIfReady();
                    }

                    case UNREADY -> {
                        session.ready = false;
                        sendLine(out, ServerCommand.STATUS_UPDATE_UNREADY + " Unreadied.");
                        System.out.println("[SERVER] Player" + session.id + "(" + session.name + ") not ready.");
                        broadcastLobby();
                    }

                    case PLAY -> {
                        if (!gameStarted) {
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
                        theMind.validatePlayedSequence();
                        broadcastGameState();
                    }

                    case NEXT_ROUND -> {
                        // TODO: if current round was VICTORY and next round is possible,
                        //       advance round in The Mind instance and broadcast new state
                        sendLine(out, ServerCommand.PLACEHOLDER + " next_round_requested");
                    }

                    case QUIT -> {
                        sendLine(out, ServerCommand.PLACEHOLDER + " bye");
                        System.out.println("[Server] Client " + session.name + " requested quit");
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
            try {
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(p.broadcastSocket.getOutputStream(), StandardCharsets.UTF_8));
                out.write(msg);
                out.write(END_OF_LINE);
                out.flush();
            } catch (IOException e) {
                System.out.println("[SERVER] Failed to send game state to " + p.name + ": " + e);
            }
        }
    }

    private void broadcastVictory() {
        // TODO
    }

    private void broadcastDefeat() {
        // TODO
    }

    // Check if enough players are present and all are ready; if so, start round 0.
    private void tryGameStartIfReady() {
        if (players.isEmpty() || players.size() <= 1) {
            return;
        }

        for (PlayerSession p : players) {
            if (!p.ready) {
                return; // someone not ready yet
            }
        }

        try {
            theMind = new Game(players.size(), 5);
            gameStarted = true;
            System.out.println("[SERVER] Game started with " + players.size() + " players.");

            broadcastGameState();

        } catch (Exception e) {
            System.out.println("[SERVER] Failed to start Game: " + e);
            e.printStackTrace(); // TODO Notify all players
        }
    }
}

