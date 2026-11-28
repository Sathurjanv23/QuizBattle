# QuizBattle 2.0 – Member Contributions & Responsibilities

This document outlines the architectural responsibilities, core concepts, assigned modules, UI screens, and verification tests for each of the five members of the QuizBattle 2.0 university project.

---

## Member 1: Sathurjan (Network Foundation & TCP Socket Programming)

- **Evaluated Concept:** Traditional TCP Socket Programming (`java.net.Socket`, `java.net.ServerSocket`), framing, and client-server foundation.
- **JavaFX UI Responsibilities:**
  - `WelcomeView`: Username entry, client validation, welcoming UX.
  - `ManualConnectView`: Direct IP/host and port connection, connection status feedback, validation error reporting.
  - `PlayerHomeView`: Initial single-player quiz screen, option selection, score display.
- **Key Backend & Protocol Classes:**
  - `com.quizbattle.client.net.SocketNetworkClient`: Thread-safe background socket reader and writer, reconnect handling, disconnect clean-up.
  - `com.quizbattle.server.blocking.SimpleQuizServer`: Reference blocking multi-threaded TCP server using worker thread pools.
  - `com.quizbattle.protocol.JsonMessageCodec` & `com.quizbattle.protocol.StreamFrameAccumulator`: Newline-delimited JSON framing, 64 KiB payload protection, UTF-8 streaming parser.
  - `com.quizbattle.protocol.Message` & `com.quizbattle.protocol.MessageType`: Wire protocol schemas and request/event contracts.
- **Verification Tests:**
  - `com.quizbattle.integration.TcpQuizIntegrationTest`: End-to-end TCP connection, message exchange, and question answering.
  - `com.quizbattle.protocol.JsonMessageCodecTest`: Codec serialization and deserialization test.
  - `com.quizbattle.protocol.StreamFrameAccumulatorTest`: TCP stream fragmentation, chunked delivery, and buffer handling.

---

## Member 2: Kirusigan (Java NIO & Multiplayer Room Management)

- **Evaluated Concept:** Non-blocking I/O (`java.nio.channels.Selector`, `ServerSocketChannel`, `SocketChannel`) and scalable concurrent room management.
- **JavaFX UI Responsibilities:**
  - `HostDashboardView`: Room creation, room joining, Quick Play entry, server connection stats.
  - `CreateRoomView`: Custom room name and capacity selection, room code generation.
  - `JoinRoomView`: Room code validation and joining flow.
  - `MultiplayerLobbyView`: Live player list, host indicator, player readiness, category selection button.
- **Key Backend Classes:**
  - `com.quizbattle.server.nio.NioQuizServer`: Event-driven non-blocking I/O selector loop handling multiple client connections concurrently without per-client threads.
  - `com.quizbattle.server.concurrent.ConcurrentRoomManager`: Thread-safe room lifecycle, room code generation, participant registration, broadcast routing.
- **Verification Tests:**
  - `com.quizbattle.integration.NioMultiplayerIntegrationTest`: Concurrent client connections, room creation, multi-client joining, room state broadcasts.
  - `com.quizbattle.server.concurrent.ConcurrentRoomManagerTest`: Thread safety of room creation, capacity limits, host migration, and player leaves.

---

## Member 3: Sandip Madhusanka (Multithreading & Question Timers)

- **Evaluated Concept:** Multithreading, scheduled task orchestration (`ScheduledExecutorService`), countdown timers, category banks, and automatic quiz progression.
- **JavaFX UI Responsibilities:**
  - `QuizCategoryView`: Visual cards for quiz categories (Java Programming, Computer Networks, General Trivia).
  - Countdown timer progress bar and remaining seconds display in `PlayerHomeView`.
  - Immediate visual feedback on answer choices (`.option-correct` / `.option-incorrect`) with scheduled delay before the next question.
- **Key Backend Classes:**
  - `com.quizbattle.server.DefaultServerMessageDispatcher`: Per-room `ScheduledExecutorService` orchestrating 20-second question deadlines, automatic progression on timeout, immediate closing when all players answer, and a 2-second answer feedback pause.
  - `com.quizbattle.server.game.DefaultQuizGameService`: Multi-category JSON question loader, question randomization, point valuation, and authoritative answer verification.
- **Verification Tests:**
  - `com.quizbattle.integration.QuestionTimerIntegrationTest`: Independent room deadlines, auto-progression, late submission rejection (`LATE`), and answer feedback pauses.

---

## Member 4: Madushika Dilshari (UDP LAN Discovery)

- **Evaluated Concept:** UDP Multicast/Broadcast Socket Programming (`java.net.DatagramSocket`, `java.net.DatagramPacket`) and automatic zero-configuration LAN service discovery.
- **JavaFX UI Responsibilities:**
  - `LanDiscoveryView`: Animated server discovery list, real-time discovered room/server cards, manual connect fallback, refresh action.
- **Key Backend & Discovery Classes:**
  - `com.quizbattle.discovery.UdpDiscoveryResponder`: Daemon thread listening on UDP port 5001 responding to discovery probes with server host, TCP port, active rooms, and server ID.
  - `com.quizbattle.discovery.UdpDiscoveryClient`: Broadcasts discovery queries across local network interfaces, aggregates responses, and dedupes discovered servers.
  - `com.quizbattle.discovery.DiscoveryProtocol`: Datagram packet encoding and parsing for discovery requests and responses.
- **Verification Tests:**
  - `com.quizbattle.discovery.UdpDiscoveryTest`: UDP broadcast loopback discovery, timeout handling, concurrent client probes, and malformed packet tolerance.

---

## Member 5: Tharuki Namasena (Thread Synchronization & Leaderboards)

- **Evaluated Concept:** Concurrency control and thread synchronization (`ReentrantReadWriteLock`, atomic variables), thread-safe scoring, and race condition prevention.
- **JavaFX UI Responsibilities:**
  - `LeaderboardView`: Live leaderboard rankings, rank badges, player names, correct counts, scores, and offline indicators.
  - `FinalResultsView`: Podium winner banner, tie-breaking display, full final standings, navigation back to lobby.
  - `PersonalSummaryView`: Breakdown of player's individual question-by-question performance and accuracy.
- **Key Backend Classes:**
  - `com.quizbattle.server.sync.ScoreManager`: High-performance thread-safe scoreboard backed by `ReentrantReadWriteLock` for concurrent read access and synchronized atomic updates.
  - `com.quizbattle.server.sync.RaceConditionDemo`: Demonstrates the critical difference between unsynchronized concurrent score mutation (showing lost updates) and `ScoreManager` synchronized safety.
- **Verification Tests:**
  - `com.quizbattle.server.sync.ScoreManagerTest`: 80+ granular unit tests covering concurrent reads/writes, tie-breaking rules, disconnect handling, and lock safety.
  - `com.quizbattle.integration.FiveClientScoredQuizTest`: Comprehensive 5-client, 10-question simulated tournament verifying scoring correctness and ranking order.
  - `com.quizbattle.integration.FiveClientDemonstrationTest`: Automated multi-client test for presentation and physical evaluation.

---

## Summary Matrix

| Member | Name | Core Technology | Primary Packages | Key Test |
|---|---|---|---|---|
| **Member 1** | Sathurjan | TCP Sockets & Framing | `client.net`, `server.blocking`, `protocol` | `TcpQuizIntegrationTest` |
| **Member 2** | Kirusigan | Java NIO & Rooms | `server.nio`, `server.concurrent` | `NioMultiplayerIntegrationTest` |
| **Member 3** | Sandip Madhusanka | Multithreading & Timers | `server.game`, `server` (dispatcher) | `QuestionTimerIntegrationTest` |
| **Member 4** | Madushika Dilshari | UDP Discovery | `discovery` | `UdpDiscoveryTest` |
| **Member 5** | Tharuki Namasena | Synchronization & Scoring | `server.sync` | `ScoreManagerTest`, `FiveClientScoredQuizTest` |
