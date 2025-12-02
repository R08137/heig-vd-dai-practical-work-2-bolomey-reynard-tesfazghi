package ch.heigvd.dai.commands;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.ArrayDeque;
import java.util.Deque;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClientUI implements AutoCloseable {

    private final Screen screen;
    private volatile String lobbyText = "Lobby not yet received...";
    private volatile String serverText = "No server response yet.";
    private volatile String inputText = "";
    private volatile boolean running = true;

    private static class Msg {
        final String text;
        final TextColor color;
        Msg(String t, TextColor c) { text = t; color = c; }
    }

    private final Deque<Msg> serverMessages = new ArrayDeque<>();

    public ClientUI() throws IOException {
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        this.screen = new TerminalScreen(terminal);
        this.screen.startScreen();
        this.screen.setCursorPosition(null);
    }

    /**
     * Main UI loop (runs in current thread).
     * Calls onCommand every time user presses ENTER with a non-empty line.
     */
    public void run(Consumer<String> onCommand) throws IOException {
        redraw();

        while (running) {
            KeyStroke key = screen.readInput();
            if (key == null) {
                continue;
            }

            KeyType type = key.getKeyType();
            switch (type) {
                case Character -> {
                    char c = key.getCharacter();
                    // basic input; you can add filtering if needed
                    inputText += c;
                    redraw();
                }
                case Backspace -> {
                    if (!inputText.isEmpty()) {
                        inputText = inputText.substring(0, inputText.length() - 1);
                        redraw();
                    }
                }
                case Enter -> {
                    String cmd = inputText.trim();
                    inputText = "";
                    redraw();
                    if (!cmd.isEmpty()) {
                        onCommand.accept(cmd);
                    }
                }
                case Escape -> {
                    // Optional: ESC quits the TUI
                    running = false;
                }
                default -> {
                    // ignore other keys
                }
            }
        }
    }

    public void updateLobby(String text) {
        lobbyText = text;
        safeRedraw();
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    public void updateServerText(String text) {
        String ts = "[" + LocalTime.now().format(TS) + "] ";

        TextColor color =
                text.startsWith("[WARNING]") ? TextColor.ANSI.YELLOW : TextColor.ANSI.WHITE;

        synchronized (serverMessages) {
            serverMessages.addLast(new Msg(ts + text, color));

            while (serverMessages.size() > 4) {
                serverMessages.removeFirst();
            }
        }
        safeRedraw();
    }



    private void safeRedraw() {
        try {
            redraw();
        } catch (IOException e) {
            // At this point there’s not much to do except stop the UI
            running = false;
        }
    }

    private void redraw() throws IOException {
        synchronized (screen) {
            screen.clear();
            TerminalSize size = screen.getTerminalSize();
            TextGraphics g = screen.newTextGraphics();

            int width = size.getColumns();
            int height = size.getRows();

            // Layout is as follows:
            // top third   -> lobby
            // middle third-> server response
            // bottom line -> input

            int bottomHeight = 3;
            int topHeight = Math.max(5, (int)(height * 0.6));
            int minMiddle = 5;
            int middleHeight = Math.max(minMiddle, height - topHeight - bottomHeight);


            // Draw borders (optional, purely cosmetic)
            g.setForegroundColor(TextColor.ANSI.WHITE);
            for (int col = 0; col < width; col++) {
                g.putString(col, topHeight, "-");
                g.putString(col, topHeight + middleHeight, "-");
            }

            // Top area: lobby
            drawMultiline(g, 0, 0, width, topHeight, lobbyText);

            // Middle area: server response
            int row = topHeight + 1;

            synchronized (serverMessages) {
                for (Msg m : serverMessages) {
                    if (row >= topHeight + middleHeight) break;

                    g.setForegroundColor(m.color);
                    String line = truncate(m.text, width);
                    g.putString(0, row, line);

                    row++;
                }
            }

            // restore default color so bottom input is not yellow
            g.setForegroundColor(TextColor.ANSI.WHITE);

            // Bottom area: user input
            String inputLabel = "> " + inputText;
            g.putString(0, topHeight + middleHeight + 1, truncate(inputLabel, width));

            screen.refresh();
        }
    }

    private void drawMultiline(TextGraphics g, int x, int y, int width, int height, String text) {
        String[] lines = text.split("\\R");
        for (int i = 0; i < height && i < lines.length; i++) {
            String line = truncate(lines[i], width);
            g.putString(x, y + i, line);
        }
    }

    private String truncate(String s, int maxWidth) {
        if (s.length() <= maxWidth) {
            return s;
        }
        if (maxWidth <= 3) {
            return s.substring(0, maxWidth);
        }
        return s.substring(0, maxWidth - 3) + "...";
    }

    @Override
    public void close() throws IOException {
        running = false;
        screen.stopScreen();
    }
}
