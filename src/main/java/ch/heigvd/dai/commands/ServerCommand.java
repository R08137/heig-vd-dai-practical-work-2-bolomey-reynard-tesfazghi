package ch.heigvd.dai.commands;

/**
 * Enumeration of all commands that the server can send to a client.
 * <p>
 * These messages encode lobby state, game state, warnings, errors, and
 * control signals (such as connection close).
 */
enum ServerCommand {
    ID_ASSIGN,
    NAME_VALIDATED,
    LOBBY_STATE,
    GAME_STATE,
    CARD_PLAYED,
    VICTORY,
    DEFEAT,
    RESET_ISSUED,
    STATUS_UPDATE_READY,
    STATUS_UPDATE_UNREADY,
    WARNING_COMMAND_INVALID,
    WARNING_NAME_WITH_READY,
    WARNING_NAME_TAKEN,
    WARNING_ALREADY_READY,
    WARNING_ALREADY_NOT_READY,
    WARNING_GAME_IN_SESSION,
    WARNING_GAME_NOT_STARTED,
    WARNING_DECK_EMPTY,
    WARNING_CARD_NOMATCH,
    WARNING_CARD_SYNTAX,
    WARNING_CARD_COOLDOWN,
    WARNING_RESET_NOT_AVAILABLE,
    WARNING_DEFEAT_WAIT_FOR_COUNTDOWN,
    ERROR_LOBBY_FULL,
    ERROR_FATAL,
    CLOSE_CONNECTION,
    PLACEHOLDER
}