package com.quizbattle.server.sync;

import com.quizbattle.model.LeaderboardEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Member 5: the single authoritative score keeper for every quiz room.
 *
 * <h2>Concurrency strategy</h2>
 * <ul>
 *   <li>{@code rooms} is a {@link ConcurrentHashMap} so different rooms can be looked up and
 *       created concurrently without a global lock.</li>
 *   <li>Each room owns a private {@link RoomScoreboard#lock}. <b>Every</b> read or write of that
 *       room's mutable state (open question, answered-sets, scores, connection flags) happens inside
 *       {@code synchronized (board.lock)}. Rooms never block each other.</li>
 *   <li>The answer path "is the question open? &rarr; has this player already answered? &rarr;
 *       record answer &rarr; add points" is a multi-step check-then-act. A ConcurrentHashMap alone
 *       cannot make that sequence atomic (two threads could both pass the "not answered yet" check
 *       and both add points). Holding the room lock for the whole sequence makes it one critical
 *       section, so a player can earn points for a question <b>at most once</b>.</li>
 *   <li>{@link #closeQuestion} takes the same lock, so an answer racing with a close is either fully
 *       counted (it got the lock first) or rejected as {@link RejectReason#LATE} (close got it first),
 *       never half-applied.</li>
 *   <li>{@link #snapshot} copies the state under the lock, so a leaderboard is always a consistent
 *       point-in-time view (no player shows a score from "after" while another shows "before").</li>
 * </ul>
 *
 * <p>Who calls {@link #closeQuestion}: today the host's NEXT_QUESTION action in
 * {@code DefaultServerMessageDispatcher}. Member 3's ScheduledExecutorService deadline task can call
 * the same method; it is idempotent, so a timer close and a host close racing each other is safe.</p>
 */
public class ScoreManager {

    private static final Logger log = LoggerFactory.getLogger(ScoreManager.class);

    /** Fixed points per correct answer. No time bonus. */
    public static final int POINTS_PER_CORRECT = 10;

    /** Why an answer was not counted. */
    public enum RejectReason {
        /** The player already answered this question. */
        DUPLICATE,
        /** The question is closed, or it is not the room's current open question. */
        LATE,
        /** The player is not a participant of this room's quiz (or the answer named another room). */
        WRONG_ROOM,
        /** The room has no quiz running. */
        NO_ACTIVE_QUIZ
    }

    /** Immutable result of one answer submission. */
    public record AnswerOutcome(
            String questionId,
            boolean accepted,
            boolean correct,
            int pointsAwarded,
            int totalScore,
            RejectReason rejectReason) {

        static AnswerOutcome rejected(String questionId, RejectReason reason, int totalScore) {
            return new AnswerOutcome(questionId, false, false, 0, totalScore, reason);
        }
    }

    /** Immutable, consistent per-room leaderboard view. */
    public record LeaderboardSnapshot(
            String roomId,
            List<LeaderboardEntry> entries,
            int questionsClosed,
            int totalQuestions,
            boolean finished) {

        public LeaderboardSnapshot {
            entries = List.copyOf(entries);
        }

        /** All players sharing rank 1 with a positive score (empty if nobody scored). */
        public List<LeaderboardEntry> winners() {
            List<LeaderboardEntry> winners = new ArrayList<>();
            for (LeaderboardEntry e : entries) {
                if (e.getRank() == 1 && e.getScore() > 0) {
                    winners.add(e);
                }
            }
            return winners;
        }
    }

    /** Mutable per-player tally. Only touched while holding the owning room's lock. */
    private static final class PlayerTally {
        final String playerId;
        String username;
        int score;
        int correctCount;
        int answeredCount;
        boolean connected = true;

        PlayerTally(String playerId, String username) {
            this.playerId = playerId;
            this.username = username;
        }
    }

    /** Mutable per-room state. Every field is guarded by {@link #lock}. */
    private static final class RoomScoreboard {
        final Object lock = new Object();
        final int totalQuestions;
        final Map<String, PlayerTally> players = new HashMap<>();
        final Map<String, Set<String>> answeredByQuestion = new HashMap<>();
        final Set<String> closedQuestions = new HashSet<>();
        String openQuestionId;
        boolean finished;

        RoomScoreboard(int totalQuestions) {
            this.totalQuestions = totalQuestions;
        }
    }

    private final Map<String, RoomScoreboard> rooms = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // Quiz lifecycle
    // ------------------------------------------------------------------

    /**
     * Starts (or restarts) a quiz in the room with a fresh scoreboard. Previous scores for the room
     * are discarded. Players must then be registered with {@link #registerPlayer}.
     */
    public void startQuiz(String roomId, int totalQuestions) {
        Objects.requireNonNull(roomId, "roomId");
        rooms.put(key(roomId), new RoomScoreboard(Math.max(0, totalQuestions)));
        log.info("ScoreManager: new scoreboard for room {} ({} questions)", roomId, totalQuestions);
    }

    /** Returns true if the room has a scoreboard whose quiz has not finished. */
    public boolean isQuizActive(String roomId) {
        RoomScoreboard board = board(roomId);
        if (board == null) {
            return false;
        }
        synchronized (board.lock) {
            return !board.finished;
        }
    }

    /**
     * Adds a player to the room's quiz, or marks a returning player connected again.
     * Existing score is kept. Does nothing if the room has no quiz.
     */
    public void registerPlayer(String roomId, String playerId, String username) {
        RoomScoreboard board = board(roomId);
        if (board == null || playerId == null) {
            return;
        }
        synchronized (board.lock) {
            PlayerTally tally = board.players.computeIfAbsent(playerId,
                    id -> new PlayerTally(id, username != null ? username : id));
            tally.connected = true;
            if (username != null) {
                tally.username = username;
            }
        }
    }

    /** Marks a player disconnected. Their score is kept and still shown on the leaderboard. */
    public void markDisconnected(String roomId, String playerId) {
        RoomScoreboard board = board(roomId);
        if (board == null || playerId == null) {
            return;
        }
        synchronized (board.lock) {
            PlayerTally tally = board.players.get(playerId);
            if (tally != null) {
                tally.connected = false;
            }
        }
    }

    /**
     * Opens a question for answers. Any question that is still open is closed first, so a room has
     * at most one open question.
     *
     * @return false if the room has no active quiz or the question was already closed earlier
     */
    public boolean openQuestion(String roomId, String questionId) {
        RoomScoreboard board = board(roomId);
        if (board == null || questionId == null) {
            return false;
        }
        synchronized (board.lock) {
            if (board.finished || board.closedQuestions.contains(questionId)) {
                return false;
            }
            if (board.openQuestionId != null && !board.openQuestionId.equals(questionId)) {
                board.closedQuestions.add(board.openQuestionId);
            }
            board.openQuestionId = questionId;
            board.answeredByQuestion.computeIfAbsent(questionId, q -> new HashSet<>());
            return true;
        }
    }

    /**
     * Closes the question so any later answer for it is rejected as {@link RejectReason#LATE}.
     *
     * <p>Idempotent and thread-safe: if the host and a timer both close the same question, exactly
     * one call returns {@code true}. Member 3's timer task should call this method.</p>
     *
     * @return true only for the call that actually closed the question
     */
    public boolean closeQuestion(String roomId, String questionId) {
        RoomScoreboard board = board(roomId);
        if (board == null || questionId == null) {
            return false;
        }
        synchronized (board.lock) {
            if (!questionId.equals(board.openQuestionId)) {
                return false;
            }
            board.openQuestionId = null;
            board.closedQuestions.add(questionId);
            log.info("ScoreManager: room {} closed question {}", roomId, questionId);
            return true;
        }
    }

    /** Ends the quiz: closes any open question and freezes scores. */
    public void finishQuiz(String roomId) {
        RoomScoreboard board = board(roomId);
        if (board == null) {
            return;
        }
        synchronized (board.lock) {
            if (board.openQuestionId != null) {
                board.closedQuestions.add(board.openQuestionId);
                board.openQuestionId = null;
            }
            board.finished = true;
        }
    }

    /** The currently open question id, or null. */
    public String getOpenQuestionId(String roomId) {
        RoomScoreboard board = board(roomId);
        if (board == null) {
            return null;
        }
        synchronized (board.lock) {
            return board.openQuestionId;
        }
    }

    // ------------------------------------------------------------------
    // Answer path (the critical section)
    // ------------------------------------------------------------------

    /**
     * Records one answer. The whole check-and-add runs inside the room lock, so concurrent duplicate
     * submissions for the same player and question produce exactly one accepted outcome.
     *
     * @param roomId     room taken from the server-side session (never from the client)
     * @param playerId   player id taken from the server-side session
     * @param questionId question the client says it is answering
     * @param correct    correctness evaluated on the server against the server-held answer key
     */
    public AnswerOutcome submitAnswer(String roomId, String playerId, String questionId, boolean correct) {
        RoomScoreboard board = board(roomId);
        if (board == null) {
            return AnswerOutcome.rejected(questionId, RejectReason.NO_ACTIVE_QUIZ, 0);
        }
        synchronized (board.lock) {
            PlayerTally tally = board.players.get(playerId);
            if (tally == null) {
                return AnswerOutcome.rejected(questionId, RejectReason.WRONG_ROOM, 0);
            }
            if (board.finished || questionId == null || !questionId.equals(board.openQuestionId)) {
                return AnswerOutcome.rejected(questionId, RejectReason.LATE, tally.score);
            }
            Set<String> answered = board.answeredByQuestion.computeIfAbsent(questionId, q -> new HashSet<>());
            if (!answered.add(playerId)) {
                return AnswerOutcome.rejected(questionId, RejectReason.DUPLICATE, tally.score);
            }
            tally.answeredCount++;
            int points = 0;
            if (correct) {
                points = POINTS_PER_CORRECT;
                tally.score += points;
                tally.correctCount++;
            }
            return new AnswerOutcome(questionId, true, correct, points, tally.score, null);
        }
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    public int getScore(String roomId, String playerId) {
        RoomScoreboard board = board(roomId);
        if (board == null) {
            return 0;
        }
        synchronized (board.lock) {
            PlayerTally tally = board.players.get(playerId);
            return tally != null ? tally.score : 0;
        }
    }

    /**
     * Consistent leaderboard for the room: score descending, then username ascending
     * (case-insensitive), then playerId. Tied scores share a rank (1, 1, 3, ...).
     */
    public LeaderboardSnapshot snapshot(String roomId) {
        RoomScoreboard board = board(roomId);
        if (board == null) {
            return new LeaderboardSnapshot(roomId, Collections.emptyList(), 0, 0, false);
        }
        List<LeaderboardEntry> unranked = new ArrayList<>();
        int closed;
        int total;
        boolean finished;
        synchronized (board.lock) {
            for (PlayerTally t : board.players.values()) {
                unranked.add(new LeaderboardEntry(0, t.playerId, t.username, t.score, t.correctCount, t.connected));
            }
            closed = board.closedQuestions.size();
            total = board.totalQuestions;
            finished = board.finished;
        }
        return new LeaderboardSnapshot(roomId, rank(unranked), closed, total, finished);
    }

    /** Sorts with the stable tie order and assigns competition ranks. */
    static List<LeaderboardEntry> rank(List<LeaderboardEntry> entries) {
        List<LeaderboardEntry> sorted = new ArrayList<>(entries);
        sorted.sort(null); // LeaderboardEntry.compareTo: score desc, username asc, playerId asc
        List<LeaderboardEntry> ranked = new ArrayList<>(sorted.size());
        int rank = 0;
        int previousScore = Integer.MIN_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardEntry e = sorted.get(i);
            if (e.getScore() != previousScore) {
                rank = i + 1;
                previousScore = e.getScore();
            }
            ranked.add(new LeaderboardEntry(rank, e.getPlayerId(), e.getUsername(),
                    e.getScore(), e.getCorrectCount(), e.isConnected()));
        }
        return ranked;
    }

    private RoomScoreboard board(String roomId) {
        return roomId == null ? null : rooms.get(key(roomId));
    }

    /** Room codes are matched case-insensitively elsewhere in the server, so normalise here too. */
    private static String key(String roomId) {
        return roomId.toUpperCase(java.util.Locale.ROOT);
    }
}
