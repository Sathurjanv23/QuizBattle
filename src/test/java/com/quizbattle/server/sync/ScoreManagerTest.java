package com.quizbattle.server.sync;

import com.quizbattle.model.LeaderboardEntry;
import com.quizbattle.server.sync.ScoreManager.AnswerOutcome;
import com.quizbattle.server.sync.ScoreManager.LeaderboardSnapshot;
import com.quizbattle.server.sync.ScoreManager.RejectReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Member 5: ScoreManager rules and concurrency invariants.
 *
 * <p>Concurrency tests use an ExecutorService plus a CountDownLatch start gate, and check
 * invariants that must hold for every interleaving. No sleeps.</p>
 */
@DisplayName("Member 5: ScoreManager synchronization")
class ScoreManagerTest {

    private static final String ROOM = "QB42KD";
    private ScoreManager sm;

    @BeforeEach
    void setUp() {
        sm = new ScoreManager();
        sm.startQuiz(ROOM, 3);
        sm.registerPlayer(ROOM, "p1", "Alice");
        sm.registerPlayer(ROOM, "p2", "Bob");
        sm.openQuestion(ROOM, "q1");
    }

    // ---------------- Rules ----------------

    @Test
    @DisplayName("Correct answer earns exactly 10 points; wrong answer is accepted with 0")
    void fixedPoints() {
        AnswerOutcome a = sm.submitAnswer(ROOM, "p1", "q1", true);
        AnswerOutcome b = sm.submitAnswer(ROOM, "p2", "q1", false);
        assertTrue(a.accepted());
        assertEquals(10, a.pointsAwarded());
        assertEquals(10, a.totalScore());
        assertTrue(b.accepted());
        assertFalse(b.correct());
        assertEquals(0, b.totalScore());
    }

    @Test
    @DisplayName("Second answer for the same question is rejected as DUPLICATE and scores nothing")
    void duplicateRejected() {
        sm.submitAnswer(ROOM, "p1", "q1", true);
        AnswerOutcome again = sm.submitAnswer(ROOM, "p1", "q1", true);
        assertFalse(again.accepted());
        assertEquals(RejectReason.DUPLICATE, again.rejectReason());
        assertEquals(10, sm.getScore(ROOM, "p1"));
    }

    @Test
    @DisplayName("Changing a wrong answer to a right one is still a DUPLICATE")
    void cannotChangeAnswer() {
        sm.submitAnswer(ROOM, "p1", "q1", false);
        assertEquals(RejectReason.DUPLICATE, sm.submitAnswer(ROOM, "p1", "q1", true).rejectReason());
        assertEquals(0, sm.getScore(ROOM, "p1"));
    }

    @Test
    @DisplayName("Answer after closeQuestion, or for a non-current question, is rejected as LATE")
    void lateRejected() {
        assertTrue(sm.closeQuestion(ROOM, "q1"));
        assertEquals(RejectReason.LATE, sm.submitAnswer(ROOM, "p1", "q1", true).rejectReason());

        sm.openQuestion(ROOM, "q2");
        assertEquals(RejectReason.LATE, sm.submitAnswer(ROOM, "p1", "q1", true).rejectReason(), "old question");
        assertEquals(RejectReason.LATE, sm.submitAnswer(ROOM, "p1", "q3", true).rejectReason(), "future question");
        assertEquals(0, sm.getScore(ROOM, "p1"));
    }

    @Test
    @DisplayName("An answer past its server deadline is rejected even before the close task runs")
    void deadlineRejectsBeforeScheduledClose() {
        long expiredDeadline = System.currentTimeMillis() - 1;
        AnswerOutcome outcome = sm.submitAnswer(ROOM, "p1", "q1", true, expiredDeadline);
        assertEquals(RejectReason.LATE, outcome.rejectReason());
        assertEquals(0, sm.getScore(ROOM, "p1"));
    }

    @Test
    @DisplayName("All-connected-answers close is atomic and waits for every connected player")
    void closesWhenEveryConnectedPlayerAnswered() {
        assertFalse(sm.closeQuestionIfAllConnectedAnswered(ROOM, "q1"));
        assertTrue(sm.submitAnswer(ROOM, "p1", "q1", true).accepted());
        assertFalse(sm.closeQuestionIfAllConnectedAnswered(ROOM, "q1"));

        sm.markDisconnected(ROOM, "p2");
        assertTrue(sm.closeQuestionIfAllConnectedAnswered(ROOM, "q1"));
        assertFalse(sm.closeQuestionIfAllConnectedAnswered(ROOM, "q1"));
        assertEquals(RejectReason.LATE, sm.submitAnswer(ROOM, "p1", "q1", true).rejectReason());
    }

    @Test
    @DisplayName("A closed question cannot be reopened")
    void closedQuestionCannotReopen() {
        sm.closeQuestion(ROOM, "q1");
        assertFalse(sm.openQuestion(ROOM, "q1"));
        assertNull(sm.getOpenQuestionId(ROOM));
    }

    @Test
    @DisplayName("Player not registered in the room is rejected as WRONG_ROOM; rooms are isolated")
    void wrongRoomRejected() {
        sm.startQuiz("OTHER1", 3);
        sm.registerPlayer("OTHER1", "p9", "Zed");
        sm.openQuestion("OTHER1", "q1");

        AnswerOutcome o = sm.submitAnswer(ROOM, "p9", "q1", true);
        assertEquals(RejectReason.WRONG_ROOM, o.rejectReason());
        assertEquals(0, sm.getScore("OTHER1", "p9"));
        assertEquals(0, sm.getScore(ROOM, "p9"));
    }

    @Test
    @DisplayName("Room without a quiz rejects as NO_ACTIVE_QUIZ")
    void noQuiz() {
        assertEquals(RejectReason.NO_ACTIVE_QUIZ, sm.submitAnswer("NOPE00", "p1", "q1", true).rejectReason());
    }

    @Test
    @DisplayName("Room codes are matched case-insensitively")
    void roomKeyCaseInsensitive() {
        assertTrue(sm.submitAnswer("qb42kd", "p1", "q1", true).accepted());
        assertEquals(10, sm.getScore(ROOM, "p1"));
    }

    @Test
    @DisplayName("Leaderboard: score desc, then username asc (case-insensitive); ties share rank")
    void leaderboardOrderingAndTies() {
        ScoreManager s = new ScoreManager();
        s.startQuiz("R", 2);
        s.registerPlayer("R", "d", "dave");
        s.registerPlayer("R", "c", "Carl");
        s.registerPlayer("R", "b", "bob");
        s.registerPlayer("R", "a", "Alice");
        s.openQuestion("R", "q1");
        s.submitAnswer("R", "a", "q1", true);
        s.submitAnswer("R", "b", "q1", true);
        s.submitAnswer("R", "c", "q1", true);
        s.closeQuestion("R", "q1");
        s.openQuestion("R", "q2");
        s.submitAnswer("R", "a", "q2", true);
        s.submitAnswer("R", "b", "q2", true);

        List<LeaderboardEntry> e = s.snapshot("R").entries();
        assertEquals(List.of("Alice", "bob", "Carl", "dave"), e.stream().map(LeaderboardEntry::getUsername).toList());
        assertEquals(List.of(1, 1, 3, 4), e.stream().map(LeaderboardEntry::getRank).toList());
        assertEquals(List.of(20, 20, 10, 0), e.stream().map(LeaderboardEntry::getScore).toList());
        assertEquals(2, e.get(0).getCorrectCount());
        assertEquals(List.of("Alice", "bob"),
                s.snapshot("R").winners().stream().map(LeaderboardEntry::getUsername).toList());
    }

    @Test
    @DisplayName("Disconnected player keeps score and is marked disconnected; rejoin marks connected")
    void disconnectedKeepsScore() {
        sm.submitAnswer(ROOM, "p1", "q1", true);
        sm.markDisconnected(ROOM, "p1");
        LeaderboardEntry alice = find(sm.snapshot(ROOM), "p1");
        assertEquals(10, alice.getScore());
        assertFalse(alice.isConnected());

        sm.registerPlayer(ROOM, "p1", "Alice");
        assertTrue(find(sm.snapshot(ROOM), "p1").isConnected());
        assertEquals(10, sm.getScore(ROOM, "p1"));
    }

    @Test
    @DisplayName("No winner when nobody scored; finished quiz rejects answers")
    void noWinnerAndFinished() {
        assertTrue(sm.snapshot(ROOM).winners().isEmpty());
        sm.finishQuiz(ROOM);
        assertTrue(sm.snapshot(ROOM).finished());
        assertEquals(RejectReason.LATE, sm.submitAnswer(ROOM, "p1", "q1", true).rejectReason());
    }

    // ---------------- Concurrency ----------------

    @RepeatedTest(20)
    @DisplayName("64 threads submit the same answer for the same player/question: exactly one accepted")
    void concurrentDuplicatesExactlyOneAccepted() throws Exception {
        int threads = 64;
        List<AnswerOutcome> outcomes = raceAll(threads, i -> () -> sm.submitAnswer(ROOM, "p1", "q1", true));

        long accepted = outcomes.stream().filter(AnswerOutcome::accepted).count();
        long duplicates = outcomes.stream().filter(o -> o.rejectReason() == RejectReason.DUPLICATE).count();
        assertEquals(1, accepted, "exactly one accepted score update");
        assertEquals(threads - 1, duplicates, "every other submission is a DUPLICATE");
        assertEquals(10, sm.getScore(ROOM, "p1"));
        assertEquals(1, find(sm.snapshot(ROOM), "p1").getCorrectCount());
    }

    @RepeatedTest(10)
    @DisplayName("5 players x 20 duplicate threads x 3 questions: totals stay consistent")
    void concurrentMultiPlayerTotals() throws Exception {
        ScoreManager s = new ScoreManager();
        String room = "MULTI1";
        int players = 5;
        int dupPerPlayer = 20;
        s.startQuiz(room, 3);
        for (int p = 0; p < players; p++) {
            s.registerPlayer(room, "p" + p, "Player" + p);
        }
        int totalAccepted = 0;
        for (String q : List.of("q1", "q2", "q3")) {
            s.openQuestion(room, q);
            // player p answers correctly only if p is even, so totals differ per player
            List<AnswerOutcome> outcomes = raceAll(players * dupPerPlayer, i -> {
                int p = i % players;
                return () -> s.submitAnswer(room, "p" + p, q, p % 2 == 0);
            });
            long accepted = outcomes.stream().filter(AnswerOutcome::accepted).count();
            assertEquals(players, accepted, "one accepted answer per player for " + q);
            totalAccepted += (int) accepted;
            assertTrue(s.closeQuestion(room, q));
        }
        assertEquals(15, totalAccepted);

        LeaderboardSnapshot snap = s.snapshot(room);
        int sum = 0;
        for (LeaderboardEntry e : snap.entries()) {
            int p = Integer.parseInt(e.getPlayerId().substring(1));
            int expected = p % 2 == 0 ? 30 : 0;
            assertEquals(expected, e.getScore(), e.getUsername());
            assertEquals(expected / 10, e.getCorrectCount());
            sum += e.getScore();
        }
        assertEquals(90, sum, "3 even players x 3 questions x 10 points");
        assertEquals(List.of("Player0", "Player2", "Player4", "Player1", "Player3"),
                snap.entries().stream().map(LeaderboardEntry::getUsername).toList());
    }

    @RepeatedTest(20)
    @DisplayName("Answers racing closeQuestion are either fully counted or LATE, never half-applied")
    void answersRacingClose() throws Exception {
        ScoreManager s = new ScoreManager();
        String room = "RACE01";
        int players = 40;
        s.startQuiz(room, 1);
        for (int p = 0; p < players; p++) {
            s.registerPlayer(room, "p" + p, "P" + p);
        }
        s.openQuestion(room, "q1");

        // task index == players is the closer, all others answer
        List<Object> results = raceAll(players + 1, i -> i == players
                ? (Callable<Object>) () -> s.closeQuestion(room, "q1")
                : (Callable<Object>) () -> s.submitAnswer(room, "p" + i, "q1", true));

        int accepted = 0;
        for (int i = 0; i < players; i++) {
            AnswerOutcome o = (AnswerOutcome) results.get(i);
            if (o.accepted()) {
                accepted++;
            } else {
                assertEquals(RejectReason.LATE, o.rejectReason());
            }
        }
        assertEquals(Boolean.TRUE, results.get(players), "close succeeds exactly once");
        int sum = s.snapshot(room).entries().stream().mapToInt(LeaderboardEntry::getScore).sum();
        assertEquals(accepted * 10, sum, "scoreboard matches the accepted answers exactly");
        assertEquals(RejectReason.LATE, s.submitAnswer(room, "p0", "q1", true).rejectReason());
    }

    @RepeatedTest(20)
    @DisplayName("Timer close and host close racing: exactly one closeQuestion() returns true")
    void concurrentCloseIsIdempotent() throws Exception {
        List<Boolean> results = raceAll(16, i -> () -> sm.closeQuestion(ROOM, "q1"));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
    }

    // ---------------- helpers ----------------

    /** Runs n tasks released together by a CountDownLatch; returns results in task order. */
    static <T> List<T> raceAll(int n, IntFunction<Callable<T>> factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch ready = new CountDownLatch(n);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<T> task = factory.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "workers did not reach the start gate");
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static LeaderboardEntry find(LeaderboardSnapshot snap, String playerId) {
        return snap.entries().stream().filter(e -> e.getPlayerId().equals(playerId)).findFirst().orElseThrow();
    }
}
