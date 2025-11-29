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

    private static final int UPPER_BOUND = 100;
    private static final int LOWER_BOUND = 0;

    private static final int MAX_PLAYERS = 10;
    private static final List<PlayerSession> players = new CopyOnWriteArrayList<>();
    private final AtomicInteger PlayerId = new AtomicInteger(1);

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

            // Check capacity
            if (players.size() >= MAX_PLAYERS) {
                sendLine(out, ServerCommand.ERROR_FATAL + " lobby_full");
                return;
            }

            // Create a temporary session with a random default name
            int id = PlayerId.getAndIncrement();
            String defaultName = "Player" + id;
            session = new PlayerSession(id, socket, defaultName);
            players.add(session);

            // Tell the client they are connected but not named yet
            sendLine(out, ServerCommand.PLACEHOLDER + " connected " + defaultName);
            broadcastLobbyStatus();

            String line;
            while ((line = in.readLine()) != null) {

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+", 2);
                String keyword = parts[0].toUpperCase();
                ClientCommand command = null;
                try {
                    command = ClientCommand.valueOf(keyword);
                } catch (IllegalArgumentException e) {
                    // Unknown command
                    sendLine(out, ServerCommand.WARNING_COMMAND_INVALID + " unknown_command");
                    continue;
                }
                String arg = (parts.length > 1) ? parts[1] : null;

                switch (command) {
                    case NAME -> {
                        // NAME <name> or NAME (no arg -> random nickname)
                        String newName;
                        if (arg == null || arg.isBlank()) {
                            newName = "Player" + id; // or something fancier/random
                        } else {
                            newName = arg.trim();
                        }
                        session.name = newName;
                        sendLine(out, ServerCommand.NAME_VALIDATED + " name_set " + newName);
                        broadcastLobbyStatus();
                    }

                    case QUIT -> {
                        sendLine(out, ServerCommand.PLACEHOLDER + " bye");
                        System.out.println("[Server] Client " + session.name + " requested quit");
                        return; // will jump to finally and clean up
                    }

                    case READY -> {
                        // will be implemented in next step
                        sendLine(out, ServerCommand.PLACEHOLDER + " TODO_READY");
                    }

                    case UNREADY -> {
                        // will be implemented in next step
                        sendLine(out, ServerCommand.PLACEHOLDER + " TODO_UNREADY");
                    }

                    case PLAY -> {
                        // will be implemented later
                        sendLine(out, ServerCommand.WARNING_COMMAND_INVALID + " play_not_implemented_yet");
                    }

                    case NEXT_ROUND -> {
                        // later
                        sendLine(out, ServerCommand.WARNING_COMMAND_INVALID + " next_round_not_implemented_yet");
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

    private void broadcastLobbyStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(ServerCommand.LOBBY_STATUS).append(' ');
        sb.append(players.size()).append(" players online.\n");

        for (PlayerSession p : players) {
            sb.append("      ");
            sb.append(p.id).append(':')
                    .append(p.name).append(" : ")
                    .append(p.ready ? "ready\n" : "not_ready\n");
        }

        String msg = sb.toString();

        // Send to all players
        for (PlayerSession p : players) {
            try {
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(p.socket.getOutputStream(), StandardCharsets.UTF_8));
                out.write(msg);
                out.write(END_OF_LINE);
                out.flush();
            } catch (IOException e) {
                System.out.println("[Server] Failed to send lobby status to " + p.name + ": " + e);
            }
        }

        System.out.println("[Server] Broadcast lobby status: " + msg);
    }


}
