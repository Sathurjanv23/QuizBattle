# Handover Guide: Ready for Member 3 (Multi-threading, Timed Engine & Synchronization)

Welcome Member 3! Member 2 has implemented the **Java NIO Non-Blocking Server**, **Multiplayer Room Management**, and the complete **JavaFX Host/Lobby UI Suite**. The project builds cleanly with 30 passing automated tests.

---

## 🎯 Member 3 Assignment Scope

Member 3's primary responsibilities:
1. **Multithreading & Timed Engine**:
   - Utilize `ExecutorService` and `ScheduledExecutorService` for synchronized countdown timers (e.g. 15-second per-question countdowns).
   - Implement automated question timeouts and auto-advance mechanisms.
2. **Category Selection & Match Orchestration**:
   - Allow host to select trivia categories and round lengths.
3. **Atomic Scoring & Synchronized Leaderboard Broadcasts**:
   - Thread-safe score calculation (e.g. speed-based bonus scoring using atomic accumulators).
   - Synchronized snapshot generation and broadcast of `LEADERBOARD` and `QUIZ_FINISHED` message envelopes.

---

## 🧩 Clean Extension Points Provided by Member 2

Member 2 established thread-safe extension contracts specifically so Member 3's background threads never write unsafely to network channels:

### 1. Thread-Safe Broadcast to Room
```java
// Available in DefaultServerMessageDispatcher:
dispatcher.broadcastToRoom(roomId, message);
```
- Completely thread-safe.
- Respects room isolation: delivers strictly to connected participants of that specific `roomId`.

### 2. Thread-Safe Send to Specific Client
```java
// Available on any ClientSession:
session.send(message);
```
- Internally enqueues the `ByteBuffer` into the session's bounded write queue and invokes `selector.wakeup()`.
- Thread-safe: can be called safely from timer threads or worker pools.

### 3. Asynchronous Server Task Queue
```java
// Available on NioQuizServer:
server.submitTask(() -> {
    // Executes inside the NIO selector loop
});
```
- Safe mechanism to execute channel modifications without race conditions.

### 4. Authoritative Room Membership & Host Checks
```java
QuizRoomService roomService = dispatcher.getRoomService();
Optional<Room> roomOpt = roomService.findRoomById(roomId);
if (roomOpt.isPresent()) {
    Room room = roomOpt.get();
    String hostId = room.getHostPlayerId();
    List<Player> players = room.getPlayers();
    boolean isHost = hostId.equals(session.getPlayerId());
}
```

---

## 🚀 5-Step Handover Workflow for Member 3

### Step 1: Branch Off `main` (After PR 02 is Merged)
```bash
git fetch origin
git checkout -b feature/03-sync-scoring-timer origin/main
mvn clean test
```
Verify that all 30 tests pass.

### Step 2: Implement Timed Quiz Engine in `server/game/`
- Introduce `ScheduledExecutorService` for 1-second countdown ticks.
- Broadcast periodic countdown warnings or tick events to the room.
- When timer expires, trigger `QUESTION_CLOSED` revealing the correct answer.

### Step 3: Implement Atomic Scoring & Leaderboard Snapshots in `server/sync/`
- Implement atomic score aggregation.
- Generate `LEADERBOARD` broadcast payloads after each question.

### Step 4: Hook Start Quiz into Timed Loop
In `DefaultServerMessageDispatcher.handleStartQuiz()`:
- Hand off match start to Member 3's timed quiz engine.

### Step 5: Verify End-to-End Operation
```bash
mvn compile exec:java -Dexec.mainClass="com.quizbattle.app.QuizBattleServerApp"
mvn javafx:run
```
