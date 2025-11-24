package ch.heigvd.dai.commands;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "server", description = "Start the server part of the network game.")
public class Server implements Callable<Integer> {

    public enum Message {
        PLAYERNAME,
        PLAYERNONAME,
        CORRECT,
        OK,
        ERROR,
    }

    // Upper bound for the card number
    private static final int UPPER_BOUND = 100;

    // Lower bound for the card number
    private static final int LOWER_BOUND = 0;

    // The highest number to played
    private int lastCardNumber;


    public static String END_OF_LINE = "\n";

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
              try (Socket socket = serverSocket.accept();
                   Reader reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
                   BufferedReader in = new BufferedReader(reader);
                   Writer writer =
                           new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                   BufferedWriter out = new BufferedWriter(writer)) {

                  System.out.println(
                          "[Server] New client connected from "
                                  + socket.getInetAddress().getHostAddress()
                                  + ":"
                                  + socket.getPort());


                  // Set game in progress
                  boolean gameInProgress = true;


                  while (!socket.isClosed()) {
                      String clientRequest = in.readLine();

                      if (clientRequest == null) {
                          socket.close();
                          continue;
                      }

                      String[] clientRequestParts = clientRequest.split(" ", 2);

                      Client.ClientCommand message = null;
                      try {
                          message = Client.ClientCommand.valueOf(clientRequestParts[0]);
                      } catch (Exception e) {
                          // Do nothing
                      }

                      String response;

                      switch (message) {
                          case NAME -> {
                              String name = clientRequestParts[1];
                              response = Message.PLAYERNAME + " " + name +END_OF_LINE;
                          }
                          case WITHOUT_NAME -> {
                              String name = "player23";
                              response = Message.PLAYERNONAME + " " + name +END_OF_LINE;
                          }
                          case PLAY -> {
                              if (clientRequestParts.length < 2) {
                                  response = Message.ERROR + " 2: the guess is not a number" + END_OF_LINE;
                                  break;
                              }

                              try {
                                  int number = Integer.parseInt(clientRequestParts[1]);

                                  if (number < LOWER_BOUND || number > UPPER_BOUND) {
                                      response =
                                              Message.ERROR + " 1: the number is not between the bounds" + END_OF_LINE;
                                  } else if (number  > lastCardNumber) {
                                      lastCardNumber = number;
                                      response = Message.CORRECT + END_OF_LINE;
                                  } else {
                                      response = Message.ERROR + " 0: generic error" + END_OF_LINE;

                                      // Set game in progress
                                      gameInProgress = false;
                                  }
                              } catch (NumberFormatException e) {
                                  response = Message.ERROR + " 2: the guess is not a number" + END_OF_LINE;
                              }
                          }
                          case RESTART -> {
                              if (gameInProgress) {
                                  response =
                                          Message.ERROR + " 1: The game is already in session" + END_OF_LINE;
                              } else {

                                  System.out.println("[Server] The game will restart" );

                                  lastCardNumber = LOWER_BOUND;
                                  gameInProgress = true;
                                  response = Message.OK + END_OF_LINE;

                              }
                          }
                          case null, default -> {
                              response = Message.ERROR + " -1: invalid message" + END_OF_LINE;
                          }
                      }

                      System.out.println(response);
                      out.write(response);
                      out.flush();
                  }

                  System.out.println("[Server] Closing connection");
              } catch (IOException e) {
                  System.out.println("[Server] IO exception: " + e);
                  return 1;
              }
          }
      } catch (IOException e) {
          System.out.println("[Server] IO exception: " + e);
          return 1;
      }


      return 0;
  }
}
