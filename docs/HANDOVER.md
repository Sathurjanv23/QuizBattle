# Handover Guide: Ready for Member 1

Welcome Member 1! The foundational architecture, protocol framing, domain models, and build configuration are established and verified. You can immediately begin your assignment without altering the directory structure.

---

## 🎯 Member 1 Assignment Scope
Implement the **basic TCP server and client networking loop**:
1. Multi-threaded blocking TCP server (`ServerSocket`) accepting client connections on port 5000.
2. Client connection loop implementing `NetworkClient` with background reading thread.
3. Handle handshake (`CONNECT` -> `CONNECTED`) and single question delivery (`QUESTION` -> `ANSWER` -> `ANSWER_RESULT`).

---

## 🚀 5-Step Handover Guide

### Step 1: Sync Your Local Branch
Fetch and branch off `chore/00-project-foundation` (or `main` after PR merge):
```bash
git fetch origin
git checkout -b feature/01-tcp-quiz origin/chore/00-project-foundation
mvn -B clean test
```
Verify all tests pass and your Java 21 environment is active.

### Step 2: Implement Blocking TCP Server in `server.blocking`
- Open `src/main/java/com/quizbattle/server/blocking/BlockingServerStub.java`.
- Rename or implement `BlockingQuizServer` implementing `QuizServer`.
- Bind `ServerSocket` to `AppConfig.getServerPort()` (default: 5000).
- In an accept loop, accept `Socket` connections and dispatch each to a worker thread (e.g. using `Executors.newCachedThreadPool()` or `Thread.ofVirtual().start()`).
- In the client handler thread:
  - Create a `ClientSession` implementation.
  - Read lines from `BufferedReader` (UTF-8).
  - Use `JsonMessageCodec.decode(line)` to obtain a `Message`.
  - Pass the message to `ServerMessageDispatcher.dispatch(session, message)`.

### Step 3: Implement Socket Network Client in `client.net`
- Create `SocketNetworkClient.java` implementing `NetworkClient`.
- In `connect(host, port)`:
  - Open `new Socket(host, port)`.
  - Launch a background daemon reading thread.
  - Read lines ending in `\n`, decode via `JsonMessageCodec`.
  - Invoke registered listeners via `UiCallbackDispatcher.runOnFxThread()`.
- In `send(Message message)`:
  - Encode message with `JsonMessageCodec.encodeToString(message)`.
  - Write bytes to socket `OutputStream` and `flush()`.

### Step 4: Wire Message Dispatch for Handshake & First Question
- In `QuizBattleServerApp.java`, initialize your blocking server and register handlers for:
  - `MessageType.CONNECT`: return `MessageType.CONNECTED` with generated `playerId`.
  - `MessageType.START_QUIZ`: read a sample question from `resources/questions/sample_questions.json` and broadcast `MessageType.QUESTION`.
  - `MessageType.ANSWER`: validate answer and send `MessageType.ANSWER_RESULT`.

### Step 5: Verify With End-to-End Launch
1. Launch Server in Terminal 1:
   ```bash
   mvn compile exec:java -Dexec.mainClass="com.quizbattle.app.QuizBattleServerApp"
   ```
2. Launch Client in Terminal 2:
   ```bash
   mvn javafx:run
   ```
3. Type a username, click **"Join Arena"**, and verify the client connects and displays the question!
