package ch.heigvd.dai.commands;

import java.io.*;
import java.util.List;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import picocli.CommandLine;

@CommandLine.Command(name = "server", description = "Start the server part of the network game.")
public class Server implements Callable<Integer> {

    private static final int MAX_PLAYERS = 10;

    // global lobby
    private static final List<PlayerSession> players = new CopyOnWriteArrayList<>();
    private final AtomicInteger playerId = new AtomicInteger(1);
    private static boolean gameStarted = false;

    // per-player connection/session to identify them for game instance
    private static class PlayerSession {
        final int id;
        final Socket socket;
        String name;
        boolean ready;

        PlayerSession(int id, Socket socket, String name) {
            this.id = id;
            this.socket = socket;
            this.name = name;
            this.ready = false;
        }
    }

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

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER] Listening on port " + port);

            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> ClientHandler(socket));
                } catch (IOException e) {
                    System.out.println("[Server] IO exception on accept: " + e);
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("[Server] IO exception: " + e);
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
                    "[Server] New client connected from "
                            + socket.getInetAddress().getHostAddress()
                            + ":"
                            + socket.getPort());

            // capacity check
            if (players.size() >= MAX_PLAYERS) {
                sendLine(out, ServerCommand.ERROR_LOBBY_FULL + " lobby_full");
                return;
            }

            // temporary session with default name; will be updated by NAME
            int id = playerId.getAndIncrement();
            String defaultName = "Player" + id;
            session = new PlayerSession(id, socket, defaultName);
            players.add(session);

            // notify client connection
            broadcastLobbyStatus();

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
                        if(session.ready) {
                            sendLine(out, ServerCommand.WARNING_CANT_NAME_WHEN_READY);
                            continue;
                        }
                        for (PlayerSession player : players) {
                            if(player.name.equals(arg)) {
                                sendLine(out, ServerCommand.WARNING_NAME_TAKEN);
                            }
                        }
                        session.name = arg;
                        sendLine(out, ServerCommand.NAME_VALIDATED + " " +arg);
                        broadcastLobbyStatus();
                    }

                    case READY -> {
                        session.ready = true;
                        sendLine(out, ServerCommand.STATUS_UPDATE_READY + " Readied.");
                        broadcastLobbyStatus();
                        tryStartRoundIfReady();
                    }

                    case UNREADY -> {
                        session.ready = false;
                        sendLine(out, ServerCommand.STATUS_UPDATE_UNREADY + " Unreadied.");
                        broadcastLobbyStatus();
                    }

                    case PLAY -> {
                        if (!gameStarted) {
                            sendLine(out, ServerCommand.WARNING_GAME_NOT_STARTED);
                            continue;
                        }
                        if (arg == null || arg.isBlank()) {
                            sendLine(out, ServerCommand.WARNING_COMMAND_INVALID + " missing_card_value");
                            continue;
                        }
                        int card;
                        try {
                            card = Integer.parseInt(arg.trim());
                        } catch (NumberFormatException e) {
                            sendLine(out, ServerCommand.WARNING_COMMAND_INVALID + " card_not_a_number");
                            continue;
                        }

                        // TODO: validate play against The Mind instance, update game state
                        // - Check cooldown (1.5 s)
                        // - Check if card belongs to this player's hand
                        // - Check misplay (DEFEAT) or progress toward VICTORY
                        // For now, just echo placeholder:
                        sendLine(out, ServerCommand.PLACEHOLDER + " play_received " + card);
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

            System.out.println("[Server] Client disconnected: " + (session != null ? session.name : "unknown"));
        } catch (IOException e) {
            System.out.println("[Server] IO exception in client handler: " + e);
        } finally {
            if (session != null) {
                players.remove(session);
                System.out.println("[Server] Removed player " + session.name);
                broadcastLobbyStatus();
                // TODO: if a player quits mid-round, update The Mind instance victory conditions
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


    // Broadcasts lobby state -> for now only does it server side.
    private void broadcastLobbyStatus() {
//        StringBuilder sb = new StringBuilder();
//        sb.append(ServerCommand.LOBBY_STATUS).append(' ');
//        sb.append(players.size()).append(" players online.\n");
//
//        for (PlayerSession p : players) {
//            sb.append("      ");
//            sb.append("Player " + p.id + " : ")
//                    .append(p.name).append(" : ")
//                    .append(p.ready ? "ready\n" : "not_ready\n");
//        }
//
//        String msg = sb.toString();
//
//        for (PlayerSession p : players) {
//            try {
//                BufferedWriter out = new BufferedWriter(
//                        new OutputStreamWriter(p.socket.getOutputStream(), StandardCharsets.UTF_8));
//                out.write(msg);
//                out.write(END_OF_LINE);
//                out.flush();
//            } catch (IOException e) {
//                System.out.println("[Server] Failed to send lobby status to " + p.name + ": " + e);
//            }
//        }
//
//        System.out.println("[Server] Broadcast lobby status: " + msg);
    }

    // Check if enough players are present and all are ready; if so, start round 0.
    private void tryStartRoundIfReady() {
        if (players.isEmpty() || players.size() <= 1) {
            return;
        }

        for (PlayerSession p : players) {
            if (!p.ready) {
                return; // someone not ready yet
            }
        }

        gameStarted = true;
        // TODO: here you would create/start The Mind game instance and deal hands
        // TODO: currentRound = 0; roundInProgress = true; etc.

        String msg = ServerCommand.GAME_STATE + " ROUND_START 0";
        for (PlayerSession p : players) {
            try {
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(p.socket.getOutputStream(), StandardCharsets.UTF_8));
                out.write(msg);
                out.write(END_OF_LINE);
                out.flush();
            } catch (IOException e) {
                System.out.println("[Server] Failed to send round start to " + p.name + ": " + e);
            }
        }

        System.out.println("[Server] Round 0 would start here (game instance TODO).");
    }
}
