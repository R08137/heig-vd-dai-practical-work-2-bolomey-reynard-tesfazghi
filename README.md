
![Java](https://img.shields.io/badge/Java-007396?style=flat-square&logo=java&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apache-maven&logoColor=white)
![Picocli](https://img.shields.io/badge/Picocli-4A90E2?style=flat-square&logo=java&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![Java Sockets](https://img.shields.io/badge/Java%20Sockets-5382A1?style=flat-square&logo=java&logoColor=white)


[![License](https://img.shields.io/github/license/heig-vd-dai-course/heig-vd-dai-course)](./LICENSE.md)
[![Latest release](https://img.shields.io/github/v/release/robielTes/heig-vd-dai-practical-work-1-bolomey-reynard-tesfazghi?include_prereleases)](https://github.com/robielTes/heig-vd-dai-practical-work-1-bolomey-reynard-tesfazghi/releases)
[![Issues](https://img.shields.io/github/issues/robielTes/heig-vd-dai-practical-work-1-bolomey-reynard-tesfazghi)](https://github.com/robielTes/heig-vd-dai-practical-work-1-bolomey-reynard-tesfazghi/issues)
[![Pull requests](https://img.shields.io/github/issues-pr/robielTes/heig-vd-dai-practical-work-1-bolomey-reynard-tesfazghi)](https://github.com/robielTes/heig-vd-dai-practical-work-1-bolomey-reynard-tesfazghi/pulls)

<a id="readme-top"></a>
# The Mind

This project implements a network-based digital recreation of the cooperative card game The Mind, where multiple players connect as separate processes and play cards in ascending order without communication. A custom application-level protocol defines how clients join, receive cards, play moves, and synchronize with the server. Communication is handled using TCP and/or UDP, enabling reliable game-state exchange across the network. The server manages the shared stack, validates plays, and enforces win/loss conditions. The system is packaged and deployed as a distributed application using Docker.

For more detail [wiki](https://github.com/reynardpaul/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi/wiki/The-Mind).

## Table of Contents

- [Requirements](#requirements)
- [Folder structure](#folder-structure)
- [Clone repository](#clone-repository)
- [Compile CLI](#compile-cli)
- [Run the CLI](#run-the-cli)
- [Test](#test)
- [Contributing](#contributing)
- [Sources](#sources)
- [License](#license)
- [Contact](#contact)

## Requirements

- [Java java 21.0.8-tem](https://adoptium.net/fr/temurin/releases?version=21)
- [Maven 3.9.11](https://maven.apache.org/)
- [picocli 4.7.6](https://picocli.info/)
- [Docker](https://www.docker.com)
- [Docker Compose](https://docs.docker.com/compose/)

## Folder structure

See the [folder structure](docs/‎folder_structure.md) documentation. We base it on the [Java TCP programming - Practical content template + Docker](https://github.com/heig-vd-dai-course/heig-vd-dai-course-java-tcp-programming-practical-content-template).

## Clone repository

```
   git clone https://github.com/reynardpaul/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi
   cd heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi
```


## Compile cli (Local)

Compile the project locally using the maven wrapper.

```shell
./mvnw clean package
```

## Run the cli  (Local)

Run the CLI without any arguments:

```sh
java -jar target/practical-work-2-bolomey-reynard-tesfazghi-1.0-SNAPSHOT.jar
```

```text
Missing required subcommand
Usage: practical-work-2-bolomey-reynard-tesfazghi-1.0-SNAPSHOT.jar [-hV]
       [COMMAND]
A small game to experiment with TCP.
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  client  Start the client part of the network game.
  server  Start the server part of the network game
```

Start the server:

```sh
java -jar target/practical-work-2-bolomey-reynard-tesfazghi-1.0-SNAPSHOT.jar server -p 6433
```

Start the client:

```sh
java -jar target/practical-work-2-bolomey-reynard-tesfazghi-1.0-SNAPSHOT.jar client -H localhost -p 6433
```

## Running with Docker Compose

```
server  → Start the server part of the network game. 
client  → Start the client part of the network game.
```
### Building the image
```sh
 docker compose buil
```
Expected output:
```
....
 [+] Building 2/2                                                                                                                                                                                          
 ✔ heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi-client  Built                                                                                                                              0.0s 
 ✔ heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi-server  Built 
```
### Start the Server (Docker)
```sh
  docker compose up server
```
Expected output:
```
....
tcp-server  | Starting server...
tcp-server  | [SERVER] Listening on port 6433 (commands)
tcp-server  | [SERVER] Listening on port 6434 (broadcasts)
```

### Start a Client (Docker)
Open a second terminal and run:
```sh
  docker compose run --rm client
```
Expected output:
```
=== Lobby ===
Players connected: 1
Players ready: 0 / 1

You are Player1.

Player list:
 - Player1 (you)










----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
[12:48:29] [INFO] Connected to server:6433
[12:49:35] Usage: NAME, READY, UNREADY, PLAY, NEXT_ROUND, QUIT, HELP








----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
> HELP
```

### Running Multiple Clients
Just run the client command multiple times, each in its own terminal:
```sh
  docker compose run --rm client
  docker compose run --rm client
  docker compose run --rm client 
  docker compose run --rm client
  docker compose run --rm client
```
Each will appear in the lobby as a separate player.

### Stop the Server 
```sh
docker compose down server
```
Each will appear in the lobby as a separate player.

## GitHub Container Registry (GHCR)

### Build and publish the Docker image 

* Log in to GitHub Container Registry

Create a GitHub Personal Access Token (PAT) with `write:packages` and `read:packages`, then:

```bash
docker login ghcr.io -u YOUR_GITHUB_USERNAME 
--password-stdin YOUR_GHCR_PAT
```
* Build the image From the root of the repository:
```bash
  docker build -t ghcr.io/YOUR_GITHUB_USERNAME/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi:latest .
```

* Push the image
```bash
  docker push ghcr.io/YOUR_GITHUB_USERNAME/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi:latest
```
After this, the image will be available at:
```text
 ghcr.io/YOUR_GITHUB_USERNAME/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi:latest
```

## Run using Docker using only GHCR

### Step 1 — Pull the image

```bash
docker pull ghcr.io/r08137/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi:latest
```

### Step 2 — Create a Docker network


```bash
docker network create themind
```
### Step 3 — Start the server container
```bash
docker run --rm -it --name themind-server --network themind -p 6433:6433 -p 6434:6434  ghcr.io/r08137/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi:latest   server -p 6433
```

If everything is correct you will see:
```text
Starting server...
[SERVER] Listening on port 6433 (commands)
[SERVER] Listening on port 6434 (broadcasts)
```
### Step 4 — Start a client in another terminal
```bash
docker run --rm -it --name themind-client-1 --network themind ghcr.io/r08137/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi:latest client -H themind-server -p 6433
```
You will see something like:
```text
[CLIENT] Assigned id 1
[INFO] Connected to themind-server:6433
```
### Step 5 — Start more clients
```bash
docker run --rm -it --name themind-client-2 --network themind ghcr.io/r08137/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi:latest client -H themind-server -p 6433
```

## Demo
[![asciicast](./docs/The%20mind.png)](https://www.youtube.com/watch?v=r8ZZr05Lqew)

## Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

If you have interested in contributing , check the [Contributing guide](https://github.com/reynardpaul/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi/wiki/Contribute) For more detail.

Thank you in advance!

## Sources
[Useful sources & resources](https://github.com/reynardpaul/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi/wiki/Source)

## License

Distributed under the Creative Commons. See [`LICENSE`](https://github.com/reynardpaul/heig-vd-dai-practical-work-2-bolomey-reynard-tesfazghi/blob/main/LICENSE.md) for more information.

## Contact

- [Bolomey Kym](https://github.com/k-bool)
- [Paul Reynard](https://github.com/reynardpaul)
- [Tesfazghi Robiel](https://github.com/R08137)

  <p align="right"><a href="#readme-top">back to top</a></p>
