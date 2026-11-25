# Handover — timer-driven quiz lifecycle

Quiz questions are server-timed and advance automatically. Standard category banks contain ten
questions, each with a 20-second deadline.

---

## What PR 05 added

| Area | Class | Notes |
|---|---|---|
| Score authority | `server/sync/ScoreManager` | The only place scores are stored. One `synchronized` lock per room around check-and-add. Fixed 10 points per correct answer. Rejects `DUPLICATE`, `LATE`, `WRONG_ROOM`, `NO_ACTIVE_QUIZ`. Consistent snapshots, tie order score desc → username asc. |
| Game service | `server/game/DefaultQuizGameService` | Delegates all scoring to `ScoreManager` (its old provisional score map is gone). Finite quiz per room (no more wrap-around), plus `closeCurrentQuestion()` / `advanceAfter()` so a question is never skipped or advanced twice. |
| Dispatcher | `server/DefaultServerMessageDispatcher` | Schedules per-question deadlines, closes and advances questions automatically, broadcasts shared timing metadata, and cleans timers on room/server shutdown. `NEXT_QUESTION` is rejected for legacy clients. |
| Live quiz | `client/ui/PlayerHomeView` | JavaFX `Timeline` renders the server deadline without creating a worker thread; options lock on answer, close, or local deadline expiry. |
| Live demo | `server/sync/RaceConditionDemo` | Unsafe vs safe, 50 latch-released threads by default. |
| JavaFX | `LeaderboardView`, `FinalResultsView`, `PersonalSummaryView` | Same CSS and `NavigationContainer`. Network callbacks already arrive on the FX thread through `UiCallbackDispatcher` (`Platform.runLater`). |
| Removed | `server/sync/LeaderboardSyncStub` | Replaced by `ScoreManager`, so there's only one scoring source. |
| Protocol | `MessageType.NEXT_QUESTION` | Documented in `PROTOCOL.md` §4.3 together with the changed `ANSWER_RESULT`. |

---

## Timer-driven progression

The server is authoritative for question start/deadline timestamps and schedules a room-scoped close
task. A question closes and advances immediately when every connected participant answers; otherwise
the deadline closes it. `ScoreManager` serializes answer submissions against both close paths, and
stale timer callbacks are guarded by the active timer identity and question ID. After question 10,
the quiz finishes without scheduling another task. The JavaFX countdown is visual only and derives
its remaining time from `deadlineMillis`.

---

## Day 4 notes for every member

- **Member 1:** `PlayerHomeView.displayAnswerResult` takes `(accepted, reason, correct, points, total)`. Players submit once per question; progression is automatic.
- **Member 2:** nothing changed in NIO or rooms. The dispatcher still calls `broadcastToRoom`. Disconnects also call `gameService.markDisconnected`.
- **Member 3:** timers use the existing `ScoreManager` through `DefaultQuizGameService`; no second score store is needed.
- **Member 4:** discovery is untouched. Run the discovery step of the demo on a second machine if one is available.
- **Everyone:** the evaluation script is `docs/DEMO.md` §1 (2 minutes each, class names listed).

## Known risks

- Starting a new quiz in a room (`START_QUIZ` or **Play Again**) clears that room's scores by design.
- In quick play (`room-default`), the first joiner after a finished or empty quiz starts a fresh timed quiz.
- Clients that disconnect and reconnect get a new `playerId` (it is derived from the session id), so they appear as a new player. The old entry stays, marked disconnected.
- One JavaFX GUI pass with five windows still needs to be done by hand before the evaluation. The automated five-client test covers the same protocol flow without the GUI.
