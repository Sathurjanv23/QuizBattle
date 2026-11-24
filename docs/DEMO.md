# QuizBattle 2.0 — Final Evaluation Runbook

This is the runbook for the physical evaluation: build checks, the 10-minute script (about 2 minutes per
member, each naming the Java class they explain), and the five-client scored quiz walkthrough.

All commands run from the project root. On Windows PowerShell keep the quotes around `-D...` arguments.

---

## 0. Before the evaluators arrive (not timed)

```powershell
java -version            # 21 or newer
mvn -version             # 3.9+
mvn -B clean test        # expect: Tests run: 131, Failures: 0, Errors: 0, Skipped: 0
```

Open these terminals in advance:

| Terminal | Command |
|---|---|
| T1 server | `mvn compile exec:java "-Dexec.mainClass=com.quizbattle.app.QuizBattleServerApp"` |
| T2–T6 clients | `mvn javafx:run` (one per player: Alice, Bob, Charlie, Diana, Evan) |
| T7 demo | `mvn -q compile exec:java "-Dexec.mainClass=com.quizbattle.server.sync.RaceConditionDemo"` |

If port 5000 is busy, stop the old server first (`Address already in use: bind` means one is still running).

---

## 1. Ten-minute script

| Time | Member | Shows | Class to explain |
|---|---|---|---|
| 0:00–2:00 | **Member 1** — TCP client/server and protocol | Start T1. Open one client and connect. Point at the newline-delimited JSON `CONNECT`/`CONNECTED` in the log. Mention the blocking reference server: `-Dquizbattle.server.transport=blocking`. | `com.quizbattle.server.blocking.BlockingQuizServer`, `com.quizbattle.client.net.SocketNetworkClient`, `com.quizbattle.protocol.StreamFrameAccumulator` |
| 2:00–4:00 | **Member 2** — Java NIO and rooms | Alice creates a room (code like `QB42KD`). Bob, Charlie, Diana and Evan join by code. The lobby roster updates live on all five clients. | `com.quizbattle.server.nio.NioQuizServer` (single `Selector` loop), `com.quizbattle.server.concurrent.ConcurrentRoomManager` (per-room `ReentrantLock`, host migration) |
| 4:00–6:00 | **Member 3** — quiz categories and question flow | Alice clicks Start Quiz, picks **Java** in the category screen, and the first question reaches every client. *(The timer engine is not merged yet: see `HANDOVER.md`.)* | `com.quizbattle.client.ui.QuizCategoryView`, `com.quizbattle.server.game.DefaultQuizGameService` (category banks, per-room question pointer) |
| 6:00–8:00 | **Member 4** — UDP LAN discovery | On a client, open the LAN discovery screen. The server on UDP 5001 answers the broadcast and Connect fills in host and port. | `com.quizbattle.discovery.UdpDiscoveryResponder`, `com.quizbattle.discovery.UdpDiscoveryClient`, `com.quizbattle.client.ui.LanDiscoveryView` |
| 8:00–10:00 | **Member 5** — synchronized scoring and results | Play the quiz to the end (section 2). Then run T7: the UNSAFE column shows double-scoring and lost updates, the SAFE column always shows `accepted=1 score=10`. | `com.quizbattle.server.sync.ScoreManager` (one `synchronized` room lock around check-and-add), `com.quizbattle.server.sync.RaceConditionDemo`, `com.quizbattle.client.ui.LeaderboardView` / `FinalResultsView` / `PersonalSummaryView` |

Member 4 can run discovery while Member 1 is talking if time is short; both only need one client.

### Member 5 talking points (≈60 seconds)

1. **One authority.** Every score lives in `ScoreManager`. `DefaultQuizGameService` only checks whether an answer is right and passes that on. The client only displays what the server sends.
2. **Why a lock and not only `ConcurrentHashMap`.** "Has this player answered? → record it → add 10" is three steps. A ConcurrentHashMap makes each step atomic, but not the sequence. `submitAnswer` runs all three inside `synchronized (board.lock)`, one lock per room, so rooms never wait for each other.
3. **Closing is part of the same discipline.** `closeQuestion` takes the same lock, so an answer racing the close is either counted in full or rejected as `LATE`. `closeQuestion` is idempotent, so a host press and a future timer can't both close the same question.
4. **Proof.** `ScoreManagerTest` starts 64 workers behind a `CountDownLatch` and asserts exactly one accepted answer, 20 times over. It also covers 5 players × 20 threads × 3 questions and answers racing a close. No sleeps.

---

## 2. Five-client scored quiz walkthrough

1. **Connect:** five clients named `Alice`, `Bob`, `Charlie`, `Diana`, `Evan` connect to `127.0.0.1:5000` (or through LAN discovery).
2. **Room:** Alice clicks **Create Multiplayer Room** and then **Enter Multiplayer Lobby**. The others click **Join Room by Code** and enter the code. The roster shows `Players: 5`.
3. **Start:** Alice clicks **Start Quiz**, chooses **Java**, and starts. Every client shows question 1 with `Points: 10`.
4. **Answer:** each player clicks an option and gets an immediate server verdict:
   - `✓ Correct! +10 points. Total Score: 10 pts`, or `✗ Incorrect choice`.
   - The options lock after one click. Even a hand-crafted second answer comes back as `Answer not counted: you already answered this question` (`DUPLICATE`).
5. **Close:** only Alice sees **End Question ➔ Leaderboard**. Clicking it reveals the correct option in green on every client, then switches everyone to the **Live Leaderboard**: rank, medal, username, correct count, score, with your own row highlighted. Ties share a rank, ordered by username.
6. **Late answer:** a player who hadn't answered sees `⌛ Question closed before you answered.` Any answer now is rejected as `LATE`.
7. **Disconnect:** close Diana's window after question 2. Her row stays on the next leaderboard with her points and a red `DISCONNECTED` badge.
8. **Next:** Alice clicks **Next Question ➔** on the leaderboard. Repeat for all 5 questions.
9. **Finish:** closing question 5 sends everyone to **Final Results**: winner banner (or tie banner), your rank/score/correct count, and the full standings.
10. **Personal Summary:** click **My Summary** to see rank, score, correct/total, accuracy and a question-by-question list. **Leave Room** returns to the dashboard. Alice also has **Play Again**.

Quick play still works: **Quick Play** on the dashboard joins `room-default`, where any player may end or advance questions.

---

## 3. Automated evidence

```powershell
mvn -B clean test
```

| Suite | Tests | What it proves |
|---|---|---|
| `ScoreManagerTest` | 81 (incl. repeated runs) | 10-point rule, DUPLICATE, LATE, WRONG_ROOM, NO_ACTIVE_QUIZ, tie order and ranks, disconnected keeps score; **64-thread latch race → exactly 1 accepted** (×20), multi-player totals (×10), answers vs close (×20), concurrent close idempotent (×20) |
| `RaceConditionDemoTest` | 10 | Demo's safe path with 100 workers always gives 1 accepted, score 10 |
| `QuizProgressionTest` | 3 | Close-then-advance moves exactly once, stale advance ignored, quiz finishes, scoring delegated |
| `FiveClientScoredQuizTest` | 1 | Full 5-client, 5-question quiz over NIO: FORBIDDEN for non-host next, DUPLICATE, WRONG_ROOM, LATE, STALE_QUESTION, disconnect keeps score, final ranking `Alice 50, Bob 30, Diana 20 (disconnected), Evan 10, Charlie 0` |
| `NioMultiplayerIntegrationTest` | 17 | NIO startup, five clients, rooms, isolation, fragmentation, partial writes, disconnects, host migration, quiz regression, graceful shutdown |
| `FiveClientDemonstrationTest` | 1 | Original 13-step lobby demonstration |
| `TcpQuizIntegrationTest` | 2 | Blocking-server quiz round and unauthorized answer |
| `UdpDiscoveryTest` | 5 | UDP discovery protocol, responder, client |
| `ModelSmokeTest`, `MessageCodecTest` | 4 + 7 | Models, masking, framing, 64 KiB cap |
| **Total** | **131** | |

Race demo with custom size: `mvn -q compile exec:java "-Dexec.mainClass=com.quizbattle.server.sync.RaceConditionDemo" "-Dexec.args=100 10"` (threads, rounds).
