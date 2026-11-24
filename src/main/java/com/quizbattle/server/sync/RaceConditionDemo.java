package com.quizbattle.server.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Member 5 live-evaluation demo: unsafe vs safe score updates under a real thread race.
 *
 * <p>This runs <b>outside</b> the NIO selector loop on purpose. The NIO server handles messages on
 * one thread, so it would hide the race. Here an {@link ExecutorService} runs many worker tasks
 * that all submit the same answer for the same player and question. A {@link CountDownLatch}
 * holds every worker at the start line and then releases them together, so they really overlap.</p>
 *
 * <p>Run: {@code mvn -q compile exec:java -Dexec.mainClass=com.quizbattle.server.sync.RaceConditionDemo}
 * (optional args: {@code -Dexec.args="<threads> <rounds>"}, defaults 50 and 5).</p>
 */
public final class RaceConditionDemo {

    private static final String ROOM = "DEMO01";
    private static final String QUESTION = "q-1";

    private RaceConditionDemo() {
    }

    /**
     * DO NOT USE: the classic broken scorer. Each step is individually "thread-safe"
     * (ConcurrentHashMap key set), but the check and the update are separate steps, and
     * {@code score += 10} is a non-atomic read-modify-write.
     */
    static final class UnsafeScorer {
        private final Set<String> answered = ConcurrentHashMap.newKeySet();
        private int score; // plain field, no lock

        boolean submit(String playerId) {
            if (!answered.contains(playerId)) {   // 1. check
                busyWait(200_000);                 //    simulated work (~0.2 ms) so the race shows on any laptop
                answered.add(playerId);            // 2. act
                int read = score;                  // 3. read
                busyWait(50_000);
                score = read + ScoreManager.POINTS_PER_CORRECT; // 4. write (may overwrite another thread)
                return true;
            }
            return false;
        }

        private static void busyWait(long nanos) {
            long end = System.nanoTime() + nanos;
            while (System.nanoTime() < end) {
                Thread.onSpinWait();
            }
        }

        int score() {
            return score;
        }
    }

    /** Result of one race round. */
    record RoundResult(int accepted, int finalScore) {
    }

    /** Races {@code threads} duplicate submissions through the unsafe scorer. */
    static RoundResult raceUnsafe(int threads) throws Exception {
        UnsafeScorer scorer = new UnsafeScorer();
        int accepted = race(threads, () -> scorer.submit("alice"));
        return new RoundResult(accepted, scorer.score());
    }

    /** Races {@code threads} duplicate submissions through the real {@link ScoreManager}. */
    static RoundResult raceSafe(int threads) throws Exception {
        ScoreManager sm = new ScoreManager();
        sm.startQuiz(ROOM, 1);
        sm.registerPlayer(ROOM, "alice", "Alice");
        sm.openQuestion(ROOM, QUESTION);
        int accepted = race(threads, () -> sm.submitAnswer(ROOM, "alice", QUESTION, true).accepted());
        return new RoundResult(accepted, sm.getScore(ROOM, "alice"));
    }

    /**
     * Starts {@code threads} tasks, holds them at a latch, releases them together and counts how
     * many returned true.
     */
    static int race(int threads, Callable<Boolean> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();   // every worker is parked at the start line
            go.countDown();  // release all of them at once
            int accepted = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    accepted++;
                }
            }
            return accepted;
        } finally {
            pool.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        System.out.println("=== QuizBattle Member 5: race-condition demo ===");
        System.out.printf("%d threads submit the SAME correct answer for the SAME player and question, released together by a CountDownLatch.%n", threads);
        System.out.printf("Correct result: exactly 1 accepted, score = %d.%n%n", ScoreManager.POINTS_PER_CORRECT);
        System.out.printf("%-6s | %-28s | %-28s%n", "Round", "UNSAFE (check-then-act)", "SAFE (ScoreManager lock)");
        System.out.println("-------+------------------------------+------------------------------");

        int unsafeBadRounds = 0;
        int safeBadRounds = 0;
        for (int r = 1; r <= rounds; r++) {
            RoundResult u = raceUnsafe(threads);
            RoundResult s = raceSafe(threads);
            boolean uOk = u.accepted() == 1 && u.finalScore() == ScoreManager.POINTS_PER_CORRECT;
            boolean sOk = s.accepted() == 1 && s.finalScore() == ScoreManager.POINTS_PER_CORRECT;
            if (!uOk) unsafeBadRounds++;
            if (!sOk) safeBadRounds++;
            System.out.printf("%-6d | accepted=%-3d score=%-4d %-4s | accepted=%-3d score=%-4d %-4s%n",
                    r, u.accepted(), u.finalScore(), uOk ? "OK" : "BAD",
                    s.accepted(), s.finalScore(), sOk ? "OK" : "BAD");
        }

        System.out.println();
        System.out.printf("UNSAFE: %d/%d rounds gave a wrong result (double-scoring or lost update).%n", unsafeBadRounds, rounds);
        System.out.printf("SAFE:   %d/%d rounds gave a wrong result.%n", safeBadRounds, rounds);
        System.out.println();
        System.out.println("Why: a ConcurrentHashMap makes each single call atomic, but 'contains' then 'add' then");
        System.out.println("'score += 10' is three steps. ScoreManager runs the whole check-and-add inside one");
        System.out.println("synchronized(room lock) block, so only one thread can pass the duplicate check.");
        if (unsafeBadRounds == 0) {
            System.out.println("(The unsafe version happened to win every round this time; try more threads or rounds.)");
        }
    }
}
