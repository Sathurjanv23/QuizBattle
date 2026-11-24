# Handover — after PR 05 (Member 5: score synchronization and results UI)

State of `main` once PR 05 is merged: PRs 00–05 are in. Build and tests: `mvn -B clean test` → 131 tests,
0 failures.

---

## What PR 05 added

| Area | Class | Notes |
|---|---|---|
| Score authority | `server/sync/ScoreManager` | The only place scores are stored. One `synchronized` lock per room around check-and-add. Fixed 10 points per correct answer. Rejects `DUPLICATE`, `LATE`, `WRONG_ROOM`, `NO_ACTIVE_QUIZ`. Consistent snapshots, tie order score desc → username asc. |
| Game service | `server/game/DefaultQuizGameService` | Delegates all scoring to `ScoreManager` (its old provisional score map is gone). Finite quiz per room (no more wrap-around), plus `closeCurrentQuestion()` / `advanceAfter()` so a question is never skipped or advanced twice. |
| Dispatcher | `server/DefaultServerMessageDispatcher` | New `NEXT_QUESTION` (host only). Sends `ANSWER_RESULT` with `accepted`/`reason`, broadcasts `QUESTION_CLOSED`, `LEADERBOARD` and `QUIZ_FINISHED`. Disconnect and leave mark the player disconnected without losing their score. |
| Live demo | `server/sync/RaceConditionDemo` | Unsafe vs safe, 50 latch-released threads by default. |
| JavaFX | `LeaderboardView`, `FinalResultsView`, `PersonalSummaryView` | Same CSS and `NavigationContainer`. Network callbacks already arrive on the FX thread through `UiCallbackDispatcher` (`Platform.runLater`). |
| Removed | `server/sync/LeaderboardSyncStub` | Replaced by `ScoreManager`, so there's only one scoring source. |
| Protocol | `MessageType.NEXT_QUESTION` | Documented in `PROTOCOL.md` §4.3 together with the changed `ANSWER_RESULT`. |

---

## Member 3 features still missing on `main` (honest list)

Member 3's category work is merged: `QuizCategoryView`, the category in `START_QUIZ`, and
`java_questions.json` / `networking_questions.json`. Still **not** implemented:

1. **Question deadlines.** No `ScheduledExecutorService` and no per-question countdown. `timeLimitSeconds` is sent to clients but nothing enforces it.
2. **Automatic close at the deadline.** Today questions close only when the host presses **End Question**.
3. **Automatic advance.** The host presses **Next Question** on the leaderboard.
4. **Live quiz / countdown screen.** The question screen is still Member 1's `PlayerHomeView`, with no countdown display.

PR 05 did **not** build these, because they are Member 3's evaluated concept.

### How Member 3 plugs the timer in (one call)

```java
// When the deadline for questionId in roomId fires (on the scheduler thread):
dispatcher.closeQuestionAndBroadcast(roomId, questionId);
// -> closes it in ScoreManager (late answers become LATE),
//    broadcasts QUESTION_CLOSED + LEADERBOARD, and QUIZ_FINISHED after the last question.
// It is thread-safe and idempotent: if the host already closed it, it returns false and does nothing.

// For auto-advance after a pause, the timer calls the same path the host uses:
gameService.advanceAfter(roomId, questionId);   // advances at most once per question
```

Look for the comment `Member 3 timer will call closeQuestion() here` in `DefaultServerMessageDispatcher`
and `DefaultQuizGameService`. Schedule the deadline when a `QUESTION` is broadcast (in `handleStartQuiz` and
in `handleNextQuestion` after `advanceAfter`), and cancel the `ScheduledFuture` on early close.

---

## Day 4 notes for every member

- **Member 1:** `PlayerHomeView.displayAnswerResult` now takes `(accepted, reason, correct, points, total)`. The old "Next Question" button became the host-only **End Question ➔ Leaderboard**, which sends `NEXT_QUESTION` instead of `START_QUIZ`. Re-sending `START_QUIZ` used to reset the room.
- **Member 2:** nothing changed in NIO or rooms. The dispatcher still calls `broadcastToRoom`. Disconnects also call `gameService.markDisconnected`.
- **Member 3:** see above. Please don't add a second score store; call `ScoreManager` through the game service.
- **Member 4:** discovery is untouched. Run the discovery step of the demo on a second machine if one is available.
- **Everyone:** the evaluation script is `docs/DEMO.md` §1 (2 minutes each, class names listed).

## Known risks

- Starting a new quiz in a room (`START_QUIZ` or **Play Again**) clears that room's scores by design.
- In quick play (`room-default`) any player may advance, and a new joiner after a finished quiz restarts it.
- Clients that disconnect and reconnect get a new `playerId` (it is derived from the session id), so they appear as a new player. The old entry stays, marked disconnected.
- One JavaFX GUI pass with five windows still needs to be done by hand before the evaluation. The automated five-client test covers the same protocol flow without the GUI.
