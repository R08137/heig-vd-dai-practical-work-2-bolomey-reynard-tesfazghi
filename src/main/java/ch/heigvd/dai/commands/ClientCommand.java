package ch.heigvd.dai.commands;

/**
 * Enumeration of all commands that a client can send to the server.
 */
enum ClientCommand {
    ID_VALIDATE,
    NAME,
    READY,
    UNREADY,
    PLAY,
    RESET,
    QUIT,
    HELP
}