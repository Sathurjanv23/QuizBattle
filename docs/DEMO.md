# QuizBattle 2.0 Demonstration Runbook (Member 2: Java NIO + Multiplayer Rooms + UI)

This guide documents the end-to-end operation, verification, and automated demonstration of the Java NIO non-blocking server and multiplayer room system.

---

## 1. Automated Test Suite Verification

Run the full automated test suite (including unit tests, protocol framing, stream fragmentation, 18 Member 2 NIO integration tests, and the 5-client demonstration):

```powershell
mvn clean test
```

### Verified Test Suites:
1. **`NioMultiplayerIntegrationTest`** (17 tests):
   - **NIO Server Startup**: Dynamic port binding and selector initialization.
   - **5 Simultaneous Clients**: Multi-client non-blocking connect and handshake.
   - **Host Room Creation**: Server-authoritative 6-character room codes (`QB42KD`).
   - **Unique Room Codes**: Collision-free generation.
   - **Multiple Players Joining**: Synchronized lobby joins.
   - **Live `ROOM_STATE` Broadcasts**: Real-time roster distribution.
   - **Unknown Room Code Rejection**: `ROOM_NOT_FOUND` error handling.
   - **Two-Room Isolation**: Cross-room message blocking.
   - **Fragmented JSON Reassembly**: TCP chunk streaming.
   - **Multiple Messages in One Read**: Concatenated packet handling with order preservation.
   - **Partial TCP Writes**: Outbound buffer resumption.
   - **Bounded Outbound Queues**: Slow client backpressure.
   - **Unexpected Disconnect**: SelectionKey and channel cleanup.
   - **Room Cleanup**: Deletion of empty ephemeral rooms.
   - **Host Disconnect & Migration**: Automatic promotion of remaining players.
   - **Selector Idle Behavior**: Zero busy-spinning verification.
   - **Quiz Question/Answer Regression**: Full quiz round execution over NIO.
2. **`FiveClientDemonstrationTest`** (1 test):
   - Full 13-step automated multiplayer demonstration.
3. **`TcpQuizIntegrationTest`** (2 tests):
   - Member 1 TCP client and unauthorized answer rejection.
4. **`ModelSmokeTest`** (3 tests):
   - Domain models, masking, and immutability.
5. **`MessageCodecTest`** (7 tests):
   - Framing, 64 KiB limits, and JSON decoding.

**Total**: 30 tests, 0 failures, 0 errors, 0 skipped.

---

## 2. Launching the Default Java NIO Server

In Terminal 1:

```powershell
mvn compile exec:java -Dexec.mainClass="com.quizbattle.app.QuizBattleServerApp"
```

### Expected Startup Output:
```
====================================================
Starting QuizBattle 2.0 Server (Member 2 Java NIO Non-Blocking Engine)
TCP Port: 5000 | UDP Discovery Port: 5001
Max Message Framing Cap: 65536 bytes (64 KiB)
Transport: Java NIO Selector [Default: Java NIO Non-Blocking]
====================================================
Initializing Java NIO Selector and ServerSocketChannel on port 5000...
NioQuizServer successfully listening on TCP port 5000 [Selector multiplexing active]
Java NIO Selector event loop started on port 5000
```

---

## 3. Five-Client Live Demonstration Walkthrough

Open 5 separate terminal tabs or PowerShell windows to launch 5 client instances:

```powershell
mvn compile exec:java -Dexec.mainClass="com.quizbattle.client.ClientLauncher"
```

### Detailed Scenario Walkthrough:

1. **Step 1: Welcome Screen (All 5 Clients)**:
   - Client 1 inputs alias: `HostAlice`
   - Client 2 inputs alias: `Bob`
   - Client 3 inputs alias: `Charlie`
   - Client 4 inputs alias: `Diana`
   - Client 5 inputs alias: `Evan`
   - Click **"Continue to Connect ➔"**.

2. **Step 2: Server Connection (All 5 Clients)**:
   - Accept default host `127.0.0.1` and port `5000`.
   - Click **"Connect to Quiz Server"**.
   - Socket connects, performs `CONNECT` handshake, receives `CONNECTED` with unique `playerId` (e.g. `usr-1a2b3c4d`).
   - All 5 clients land smoothly on the **Host Dashboard** (Screen 1).

3. **Step 3: Host Creates Room**:
   - `HostAlice` clicks **"🎮 Create Multiplayer Room"**.
   - Server validates session, generates unique 6-character room code (e.g. `QB42KD`), and sends `ROOM_CREATED`.
   - Screen 2 (**Create Room Confirmation**) appears showing the code `QB42KD` in large font.
   - Click **"📋 Copy Room Code"** (clipboard confirms `✓ Room code QB42KD copied!`).
   - Click **"Enter Multiplayer Lobby ➔"**.

4. **Step 4: Multiplayer Lobby (Screen 4)**:
   - `HostAlice` sees:
     - `ROOM CODE: QB42KD`
     - `👑 Host: HostAlice`
     - `Players: 1`
     - Player row: `👑 HostAlice (usr-...) [HOST]`
     - Active button: `🚀 Start Quiz (Host)`

5. **Step 5: Remaining 4 Players Join**:
   - `Bob`, `Charlie`, `Diana`, and `Evan` click **"🚪 Join Room by Code"** on their Dashboards (Screen 3).
   - Enter `QB42KD`.
   - Click **"Join Room ➔"**.
   - Each client receives `ROOM_JOINED` and enters the Lobby (Screen 4).
   - Non-host button displays: `⏳ Waiting for Host...` (disabled).

6. **Step 6: Live Roster Synchronization**:
   - All 5 clients display identical real-time rosters:
     - `Players: 5`
     - 1 Host (`HostAlice`) + 4 Players (`Bob`, `Charlie`, `Diana`, `Evan`).

7. **Step 7: Player Disconnect & Real-time Update**:
   - Close `Evan`'s window.
   - Within milliseconds, all remaining 4 clients update to `Players: 4` without server restart or crash.

8. **Step 8: Room Isolation Test**:
   - Launch a 6th client, create Room B (e.g. `QB99ZZ`).
   - Room B is completely isolated from Room A.

9. **Step 9: Launch Synchronized Quiz**:
   - `HostAlice` clicks **"🚀 Start Quiz (Host)"**.
   - Server delivers active `QUESTION` to all Room A participants.
   - All 4 clients transition to the trivia arena, showing the question and 4 multiple-choice options (A, B, C, D).
   - Select option A: instant feedback banner (`✓ Correct! +100 points`).
