# Handover Guide: Ready for Member 2

Welcome Member 2! Member 1 has completed the foundational TCP socket networking and basic JavaFX quiz client. The project is fully runnable end-to-end with automated test coverage.

---

## 🎯 Member 2 Assignment Scope
Member 2's primary focus is **NON-BLOCKING I/O (NIO) / CONCURRENCY & SCALABLE ROOM MANAGEMENT**:
1. Replace the temporary `BlockingQuizServer` (`java.net.ServerSocket`) with an NIO or scalable concurrent transport without altering game logic or UI presentation.
2. Maintain strict decoupling: the server transport communicates exclusively through `ServerMessageDispatcher` and `ClientSession`.
3. Preserve all client protocol invariants, framing (`\n`), and message schemas.

---

## 🧩 Switchable Architecture Interfaces

Member 1 designed the server layer to be completely switchable:

| Component | Responsibility | Status | Member 2 Action |
|---|---|---|---|
| `QuizServer` | Server lifecycle interface (`start()`, `stop()`, `getPort()`) | Stable | Implement `NioQuizServer` or concurrent server |
| `ServerMessageDispatcher` | Routes incoming `Message` envelopes to game services | Stable | **Do not modify** - reuse directly |
| `ClientSession` | Encapsulates client channel/socket state & session security | Stable | Implement `NioClientSession` wrapping `SocketChannel` |
| `QuizRoomService` | Room lifecycle & member rosters | Stable | Enhance with fine-grained lock-free concurrency |
| `DefaultQuizGameService` | 5 fixed questions & answer evaluation | Stable | **Do not modify** - reuse directly |
| `JavaFX Client UI` | Welcome, Manual Connect, and Player Home | Stable | **Do not modify** - works seamlessly over any TCP server |

---

## 🚀 5-Step Handover Guide for Member 2

### Step 1: Branch Off `main` (After PR 01 is Merged)
```bash
git fetch origin
git checkout -b feature/02-nio-concurrency origin/main
mvn clean test
```
Verify that all 12 tests pass.

### Step 2: Implement `NioClientSession` in `server.nio`
- Implement `ClientSession` wrapping a non-blocking `java.nio.channels.SocketChannel`.
- Maintain write buffers or write queues for non-blocking socket writes without blocking selector threads.

### Step 3: Implement `NioQuizServer` in `server.nio`
- Extend or replace `NioServerStub.java`.
- Open `ServerSocketChannel`, configure non-blocking mode, and register with `Selector` for `OP_ACCEPT`.
- On `OP_READ`:
  - Read incoming bytes into a per-channel `ByteBuffer`.
  - Use `StreamFrameAccumulator` or buffer split on `\n` to reassemble complete lines.
  - Decode with `JsonMessageCodec.decode(line)`.
  - Dispatch via `ServerMessageDispatcher.dispatch(session, message)`.

### Step 4: Swap Server Implementation in `QuizBattleServerApp`
In `QuizBattleServerApp.java`:
```java
// Member 1 (temporary):
// QuizServer server = new BlockingQuizServer(port, dispatcher);

// Member 2 (NIO):
QuizServer server = new NioQuizServer(port, dispatcher);
```
Notice that zero business logic or client UI code needs to change!

### Step 5: Verify End-to-End Compatibility
1. Run automated tests: `mvn clean test`.
2. Start your NIO server:
   ```bash
   mvn compile exec:java -Dexec.mainClass="com.quizbattle.app.QuizBattleServerApp"
   ```
3. Launch JavaFX Client:
   ```bash
   mvn compile exec:java -Dexec.mainClass="com.quizbattle.client.ClientLauncher"
   ```
4. Connect, join room, and submit answers. Verify that your NIO server delivers identical protocol messages without regression!
