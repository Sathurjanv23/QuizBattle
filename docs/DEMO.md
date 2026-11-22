# QuizBattle 2.0 Demonstration Runbook

This guide demonstrates how to verify and run the foundational architecture of QuizBattle 2.0.

---

## 1. Environment Verification

Check Java and Maven versions:
```bash
java -version
mvn -version
```
Expected output: Java 21+ and Apache Maven 3.9+.

---

## 2. Execute Automated Smoke Tests

Run clean build and tests:
```bash
mvn -B clean test
```

### What is tested:
1. `MessageCodecTest`:
   - Validates newline delimiter (`\n`) wire framing.
   - Validates bidirectional serialization/deserialization with Jackson.
   - Confirms strict 64 KiB payload boundary enforcement (`ProtocolException`).
   - Confirms malformed JSON handling and rejection.
2. `ModelSmokeTest`:
   - Validates domain model contracts (`Player`, `Room`, `Question`, `LeaderboardEntry`).
   - Confirms server question sanitization (`correctOptionIndex` masked for clients).
   - Confirms leaderboard rank comparator sorting.

---

## 3. Server Startup Demonstration

Execute:
```bash
mvn compile exec:java -Dexec.mainClass="com.quizbattle.app.QuizBattleServerApp"
```

### Expected Log Output:
```
====================================================
Starting QuizBattle 2.0 Server Architecture Scaffold
TCP Port: 5000 | UDP Discovery Port: 5001
Max Message Framing Cap: 65536 bytes (64 KiB)
Ready for Member 1 Blocking / Member 4 NIO server binding
====================================================
BlockingServerStub ready for Member 1 implementation on port 5000
QuizBattle Server scaffold initialized successfully.
```

---

## 4. JavaFX Client Launch Demonstration

Execute:
```bash
mvn javafx:run
```
Or:
```bash
mvn compile exec:java -Dexec.mainClass="com.quizbattle.client.ClientLauncher"
```

### Expected Result:
- A modern dark-themed window (860x600) titled **"QuizBattle 2.0"** opens.
- Header displays application title and status indicator (amber: Disconnected).
- Card displays:
  - "Welcome to QuizBattle - Concurrent Network Quiz Arena"
  - Username input field
  - Server host (`127.0.0.1`) and port (`5000`) inputs
  - "Join Arena" button
  - Discovery info note
- Clicking "Join Arena" updates status to blue ("Connecting...").
