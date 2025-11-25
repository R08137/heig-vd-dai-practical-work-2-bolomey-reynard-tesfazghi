# Section 1 - Overview

The "The Mind" protocol is a client–server application protocol that allows up to 10 players to play a digital
version of the game "The Mind" in real time.

**Server tasks:**

- Manage player connections and the lobby (up to 10 simultaneous connections).
- Create an instance of "The Mind" game and relay game information to the clients when all are readied.
- Manages client actions to setup and progress through the instance.
- Game setup:
    - The server acts as a messenger between the dealer (an instance of "The Mind" booted up server-side) and players (
      Clients).
    - Server validates and processes card plays
    - Server returns appropriate message on misplays and victory condition
    - Server broadcasts to all client game state update

**Client tasks:**

- Connect to the server to join game
- Give a nickname or accept a randomly generated one
- Ready itself for game setup
- Unready itself if desired
- Send PLAY <card> actions
- Quit if desired
- Display the received state updates and current hand

The game ends in one of two ways:

- `VICTORY`: all cards for the current round have been played; either move to the next round or end after 5 rounds.
- `DEFEAT`: a misplay is detected (a played card is higher than at least one card still in a hand); go back to setup
  phase.

# Section 2 - Protocol

The "The Mind" protocol is a line-based text protocol using TCP for reliable transmission and simplicity of
multithreaded
implementation through Executor service
The server listens on TCP port XXXX.

All messages are UTF-8 encoded strings terminated by a newline character (\n or END_OF_LINE).

Messages are treated as text commands: a command keyword in UPPERCASE, followed by space-separated parameters.

The client initiates the TCP connection.

The server may handle multiple clients concurrently (multithreaded).

## Connection lifecycle:

The client connects to the server (TCP).

The client must send a `NAME <name>` message to register as a manually named player.

The client can also send `NAME` without argument to register with a randomly generated nickname.

When enough players are present (implementation-defined, up to 10) and all players indicate `READY`, the server starts
round 0.

On each victory, the server awaits explicit `NEXT_ROUND` from the clients.

At any given time, a client may `QUIT`, which removes it from the game and closes the connection.

The server broadcasts lobby status to all clients after each validated `NAME` and `READY` command.
The server broadcasts state of the game to all clients on round start, finish, and after each `PLAY`.
During a round, the server does not accept any valid `PLAY` issued command for 1.5 second, to allow proper
acknowledgement of
the updated game state to players.

## Game lifecycle details:

**Deck**: A game deck holds 100 unique cards, numbered from 1 to 100.

**Rounds**:

Round index starts at 0.

On round r, each player receives a playing hand of 5 + r cards.

Maximum round is 5. After winning round 5, the game session is considered completely won.

**Stack**:

At the start of a round, the common stack is empty.

When a `PLAY <card>` command is accepted, the card is placed atop of the stack.

Cards must be played in strictly ascending order in order to proceed towards victory.

**Misplay**:

After a `PLAY <card>`, the server "checks" if there exists any card in any hand that is lower than <card> and not yet
played.

If such a card exists, the round is immediately lost (`DEFEAT`).

The method used to do that check is defined within the "The Mind" instance and uses a precalculated "victory" stack to
compare from.

**Victory**:

When the victory condition is met, servers returns `VICTORY` and awaits a client response `NEXT_ROUND`.

If conditions for next round are unmet, servers goes back to lobby/game setup.

## Errors and invalid messages:

If a request violates game rules or has invalid syntax, the server sends a corresponding `WARNING` message.

For unknown or malformed messages, the server responds with `WARNING_COMMAND_INVALID`.

For playing a card that isn't part of a client's hand, the server responds with `WARNING_CARD_NOMATCH`

For playing a card right after a game state update, the server responds with `WARNING_CARD_COOLDOWN`

Such warnings do not disrupts the flow of the game and invite the client to reissue a new command.

## Connection closing:

A client may close the network connection at any time through the `QUIT` command.

The server can manage players quitting mid-round by adjusting the victory conditions through the "The Mind" instance.

The server broadcasts a notice when a client connection has been closed.

If the server encounters a fatal error, it issues `ERROR_FATAL` to inform players and closes all distant connections.

# Section 3 - Messages

Summary of main commands:

**Server**:

**Client**:

# Section 4 - 