package ch.heigvd.dai.commands;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "client", description = "Start the client part of the network game.")
public class Client implements Callable<Integer> {
    public enum ClientCommand {
        NAME,
        WITHOUT_NAME,
        PLAY,
        RESTART,
        HELP,
        QUIT,
    }

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
      System.out.println("Connecting to " + host + ":" + port);

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

              try {
                  String[] userInputParts = userInput.split(" ", 2);
                  ClientCommand message = ClientCommand.valueOf(userInputParts[0].toUpperCase());

                  String request = null;

                  switch (message) {
                      case NAME -> {
                          String name = userInputParts[1];

                          request = ClientCommand.NAME + " " + name;
                      }
                      case WITHOUT_NAME ->
                          request = ClientCommand.NAME.name();

                      case PLAY -> {
                          int number = Integer.parseInt(userInputParts[1]);

                          request = ClientCommand.PLAY + " " + number + END_OF_LINE;
                      }
                      case RESTART ->
                          request = ClientCommand.RESTART + END_OF_LINE;

                      case QUIT -> {
                          socket.close();
                          continue;
                      }
                      case HELP -> help();
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


          }

          System.out.println("[Client] Closing connection and quitting...");
      } catch (Exception e) {
          System.out.println("[Client] Exception: " + e);
          return 1;
      }

      return 0;
  }

    private static void help() {
        System.out.println("Usage:");
        System.out.println("  " + ClientCommand.NAME + " <your name> - Play the game with a name.");
        System.out.println("  " + ClientCommand.WITHOUT_NAME + " - Play the game without a name and the server with generate random name.");
        System.out.println("  " + ClientCommand.PLAY + " <number> - Submit the card number you want to play.");
        System.out.println("  " + ClientCommand.RESTART + " - Restart the game.");
        System.out.println("  " + ClientCommand.QUIT + " - Quit the game and close the connection to the server.");
        System.out.println("  " + ClientCommand.HELP + " - Display this help message.");
    }
}
