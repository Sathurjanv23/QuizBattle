# QuizBattle 2.0 Demonstration Runbook (Member 1 - TCP & Basic Quiz)

This guide documents the end-to-end operation, verification, and manual testing of the basic TCP quiz system.

---

## 1. Automated Test Suite Verification

Run the full automated test suite (including unit, protocol framing, stream fragmentation, and end-to-end TCP integration tests):

```powershell
$env:JAVA_HOME = "C:\Users\User\.jdks\openjdk-26.0.1"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
mvn clean test
```

### Verified Test Suites:
1. **`MessageCodecTest`** (7 tests):
   - Newline-delimited framing validation (`\n`).
   - Request-response envelope serialization and Jackson JSON parsing.
   - 64 KiB buffer cap rejection (`ProtocolException`).
   - Malformed JSON structured rejection.
   - **TCP Stream Fragmentation Reassembly**: Simulates partial frames delivered across 3 split packets; confirms the accumulator buffers until `\n`.
   - **TCP Multi-Message Chunk Handling**: Confirms concatenated messages (`msg1\nmsg2\n`) in a single network buffer are parsed independently.
   - Buffer overflow rejection without delimiter.
2. **`TcpQuizIntegrationTest`** (2 tests):
   - **Full End-to-End Quiz Flow**: Binds `BlockingQuizServer` on an ephemeral port, connects `SocketNetworkClient`, executes handshake (`CONNECT` -> `CONNECTED`), joins room (`JOIN_ROOM` -> `ROOM_JOINED` + `QUESTION`), submits multiple-choice choice (`ANSWER` -> `ANSWER_RESULT`), and verifies authoritative player ID and score updates.
   - **Security / Authorization Validation**: Confirms that submitting an answer without prior authentication produces a structured `UNAUTHORIZED` error envelope.
3. **`ModelSmokeTest`** (3 tests):
   - Model immutability, client question masking (`correctOptionIndex` nullified), and leaderboard sorting.

**Total**: 12 tests, 0 failures, 0 errors, 0 skipped.

---

## 2. Running the Reference TCP Server

In Terminal 1, launch the blocking TCP reference server:

```powershell
$env:JAVA_HOME = "C:\Users\User\.jdks\openjdk-26.0.1"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
mvn compile exec:java -Dexec.mainClass="com.quizbattle.app.QuizBattleServerApp"
```

### Expected Startup Output:
```
====================================================
Starting QuizBattle 2.0 Server (Member 1 TCP Reference)
TCP Port: 5000 | UDP Discovery Port: 5001
Max Message Framing Cap: 65536 bytes (64 KiB)
Transport: Blocking ServerSocket (Switchable)
====================================================
Initialized DefaultQuizRoomService with default room 'General Trivia Arena' (room-default)
Loaded 5 questions from resource /questions/sample_questions.json
BlockingQuizServer started listening on TCP port 5000
Acceptor loop started on port 5000
QuizBattle Server running and accepting connections on port 5000.
```

---

## 3. Running the JavaFX Client

In Terminal 2, launch the JavaFX client application:

```powershell
$env:JAVA_HOME = "C:\Users\User\.jdks\openjdk-26.0.1"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
mvn compile exec:java -Dexec.mainClass="com.quizbattle.client.ClientLauncher"
```

*(Alternatively: `mvn javafx:run`)*

---

## 4. Manual Demonstration Walkthrough

1. **Screen 1: Welcome Screen**
   - The user is greeted by the sleek dark-theme card titled **"QuizBattle 2.0 - Multiplayer Network Trivia Arena"**.
   - Input username: e.g. `CyberNinja`.
   - Click **"Continue to Connect ➔"**.
   - *Validation test*: Leaving the field empty shows `Please enter a valid username.` without freezing.

2. **Screen 2: Manual Connect Screen**
   - Displays player alias badge: `Connecting as: CyberNinja`.
   - Shows host (`127.0.0.1`) and port (`5000`).
   - Click **"Connect to Quiz Server"**.
   - Connection indicator smoothly switches to blue (`Connecting...`) on background thread.
   - Upon TCP socket connect and server `CONNECT` handshake, receives `CONNECTED` with assigned `playerId` (e.g. `usr-a1b2c3d4`).
   - Seamlessly transitions to the Player Home screen.
   - *Error test*: Entering an unreachable port (e.g. `9999`) displays `ConnectException: Connection refused` in an alert box without crashing the UI.

3. **Screen 3: Player Home & Basic Quiz**
   - Top bar displays:
     - Player identity: `👤 CyberNinja (usr-a1b2c3d4)`
     - Connection status: `TCP: 127.0.0.1:5000` (green badge)
     - Score badge: `Score: 0 pts`
     - `Disconnect` button
   - Click **"🎯 Join Default Room"**.
   - Client sends `JOIN_ROOM` -> receives `ROOM_JOINED` (`General Trivia Arena`) and immediately receives the active question (`QUESTION`).
   - Question card displays:
     - Question metadata: `Question ID: q-101 | Points: 100 | Time: 20s`
     - Question prompt: `What is the primary difference between TCP and UDP?`
     - 4 interactive multiple-choice option buttons (A, B, C, D).
   - Click option **A** ("TCP is connection-oriented and reliable; UDP is connectionless and best-effort"):
     - Option buttons disable to prevent double answers.
     - In background, client transmits `ANSWER` message.
     - Server evaluates answer, replies with `ANSWER_RESULT`:
       - Green banner appears: `✓ Correct! +100 points. Total Score: 100 pts`
       - Top bar updates: `Score: 100 pts`.
       - **"Next Question ➔"** button appears.
   - Click **"Next Question ➔"**:
     - Client requests next question; server advances to question 2 ("In Java network programming, which method accepts an incoming TCP client connection on ServerSocket?").
     - Options reset for question 2!

4. **UI Limitations & Handover Boundary for Member 1**:
   - Reconnection logic and multi-room discovery (UDP) are out of Member 1's scope (owned by Member 5).
   - Real-time multi-client leaderboard synchronization UI is owned by Member 3 & 5.
   - Server transport uses standard blocking `ServerSocket` worker threads; scalable NIO selector replacement is Member 2's focus.
