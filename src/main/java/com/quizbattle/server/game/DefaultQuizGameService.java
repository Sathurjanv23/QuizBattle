package com.quizbattle.server.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizbattle.model.LeaderboardEntry;
import com.quizbattle.model.Question;
import com.quizbattle.model.QuizResult;
import com.quizbattle.server.sync.ScoreManager;
import com.quizbattle.server.sync.ScoreManager.AnswerOutcome;
import com.quizbattle.server.sync.ScoreManager.LeaderboardSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transport-neutral game logic engine for QuizBattle.
 *
 * <p>Owns the question banks and the per-room question pointer, and evaluates answers against the
 * server-held correct option. It holds <b>no scores</b>: every score read or write is delegated to
 * the single authority {@link ScoreManager} (Member 5).</p>
 *
 * <p>Question progression per room is guarded by that room's {@link RoomQuiz} monitor, so answer
 * submissions and deadline callbacks cannot skip or double-advance a question.</p>
 */
public class DefaultQuizGameService implements QuizGameService {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuizGameService.class);
    public static final String DEFAULT_CATEGORY = "General Trivia";

    /** Result of trying to move a room to its next question. */
    public record Advance(boolean advanced, Question nextQuestion, boolean finished) {
        static final Advance NOT_ADVANCED = new Advance(false, null, false);
    }

    /** Per-room quiz progress. All fields are guarded by the instance's own monitor. */
    private static final class RoomQuiz {
        final String category;
        final List<Question> bank;
        int index = -1;
        boolean finished;

        RoomQuiz(String category, List<Question> bank) {
            this.category = category;
            this.bank = bank;
        }

        Question current() {
            return index >= 0 && index < bank.size() ? bank.get(index) : null;
        }
    }

    private final List<Question> questionBank;
    private final Map<String, List<Question>> categoryQuestionBanks = new ConcurrentHashMap<>();
    private final Map<String, RoomQuiz> roomQuizzes = new ConcurrentHashMap<>();
    private final ScoreManager scoreManager;

    public DefaultQuizGameService() {
        this("/questions/sample_questions.json");
    }

    public DefaultQuizGameService(String resourcePath) {
        this(resourcePath, new ScoreManager());
    }

    public DefaultQuizGameService(String resourcePath, ScoreManager scoreManager) {
        this.scoreManager = Objects.requireNonNull(scoreManager, "scoreManager must not be null");
        List<Question> loaded = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                ObjectMapper mapper = new ObjectMapper();
                loaded = mapper.readValue(in, new TypeReference<List<Question>>() {});
                log.info("Loaded {} questions from resource {}", loaded.size(), resourcePath);
            } else {
                log.warn("Question resource {} not found. Falling back to default questions.", resourcePath);
            }
        } catch (Exception e) {
            log.error("Failed to load questions from {}: {}", resourcePath, e.getMessage());
        }

        if (loaded.isEmpty()) {
            loaded.add(new Question("q-101", "What is the primary difference between TCP and UDP?",
                    List.of("TCP is connection-oriented; UDP is connectionless", "UDP guarantees ordering", "TCP is layer 1", "UDP requires handshake"),
                    0, 20, ScoreManager.POINTS_PER_CORRECT));
        }

        this.questionBank = Collections.unmodifiableList(loaded);
        categoryQuestionBanks.put(DEFAULT_CATEGORY, this.questionBank);
        categoryQuestionBanks.put("Java", loadQuestionBank("/questions/java_questions.json", this.questionBank));
        categoryQuestionBanks.put("Networking", loadQuestionBank("/questions/networking_questions.json", this.questionBank));
    }

    private List<Question> loadQuestionBank(String resourcePath, List<Question> fallback) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                ObjectMapper mapper = new ObjectMapper();
                List<Question> questions = mapper.readValue(in, new TypeReference<List<Question>>() {});
                if (!questions.isEmpty()) {
                    log.info("Loaded {} questions from category resource {}", questions.size(), resourcePath);
                    return Collections.unmodifiableList(questions);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load category questions from {}: {}", resourcePath, e.getMessage());
        }
        log.warn("Category resource {} not found. Falling back to the general question bank.", resourcePath);
        return fallback;
    }

    // ------------------------------------------------------------------
    // Quiz lifecycle
    // ------------------------------------------------------------------

    /**
     * Selects the room's category and resets it to a fresh, not-yet-started quiz
     * (new empty scoreboard in {@link ScoreManager}).
     */
    public void setCategory(String roomId, String category) {
        String resolved = category != null && categoryQuestionBanks.containsKey(category) ? category : DEFAULT_CATEGORY;
        List<Question> bank = categoryQuestionBanks.getOrDefault(resolved, questionBank);
        roomQuizzes.put(key(roomId), new RoomQuiz(resolved, bank));
        scoreManager.startQuiz(roomId, bank.size());
        log.info("Room {} selected quiz category {}", roomId, resolved);
    }

    /**
     * Starts a fresh quiz: new scoreboard, registers the room's current players, opens question 1.
     *
     * @param participants playerId -> username of everyone currently in the room
     * @return the first question (client-safe copy), or empty if the bank is empty
     */
    public Optional<Question> startQuiz(String roomId, String category, Map<String, String> participants) {
        setCategory(roomId, category);
        if (participants != null) {
            participants.forEach((pid, name) -> scoreManager.registerPlayer(roomId, pid, name));
        }
        return advanceQuestion(roomId);
    }

    /** Quick-play default room: starts a quiz if none is running (or the last one finished). */
    public synchronized void ensureQuizRunning(String roomId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        boolean needsStart = quiz == null;
        if (quiz != null) {
            synchronized (quiz) {
                needsStart = quiz.finished || quiz.index < 0;
            }
        }
        if (needsStart) {
            startQuiz(roomId, DEFAULT_CATEGORY, Map.of());
        }
    }

    public boolean isQuizActive(String roomId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        if (quiz == null) {
            return false;
        }
        synchronized (quiz) {
            return !quiz.finished && quiz.index >= 0;
        }
    }

    @Override
    public void startGame(String roomId, String initiatedByPlayerId) {
        startQuiz(roomId, DEFAULT_CATEGORY, Map.of());
        log.info("Game started in room {} by player {}", roomId, initiatedByPlayerId);
    }

    @Override
    public Optional<Question> nextQuestion(String roomId) {
        return advanceQuestion(roomId);
    }

    /** Current question as sent to clients (answer hidden), or empty if no question is active. */
    public Optional<Question> getCurrentQuestion(String roomId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        if (quiz == null) {
            return Optional.empty();
        }
        synchronized (quiz) {
            Question q = quiz.finished ? null : quiz.current();
            return Optional.ofNullable(q).map(DefaultQuizGameService::toClient);
        }
    }

    /**
     * Unconditionally closes the current question (if any) and moves to the next one.
     * Used when the quiz starts. Prefer {@link #closeCurrentQuestion} + {@link #advanceAfter}
     * for in-game advancing, which protects against double-advance.
     */
    public Optional<Question> advanceQuestion(String roomId) {
        RoomQuiz quiz = roomQuizzes.computeIfAbsent(key(roomId), k -> {
            scoreManager.startQuiz(roomId, questionBank.size());
            return new RoomQuiz(DEFAULT_CATEGORY, questionBank);
        });
        synchronized (quiz) {
            Question current = quiz.current();
            if (current != null) {
                scoreManager.closeQuestion(roomId, current.getQuestionId());
            }
            return Optional.ofNullable(moveToNext(roomId, quiz)).map(DefaultQuizGameService::toClient);
        }
    }

    /**
     * Closes the room's current question in {@link ScoreManager}.
     *
     * <p>The dispatcher calls this from the scheduled deadline task when the server's time limit
     * expires.</p>
     *
     * @param expectedQuestionId if non-null, only close when this is still the current question
     * @return the closed question (with correct answer, server-side copy) only if this call closed it
     */
    public Optional<Question> closeCurrentQuestion(String roomId, String expectedQuestionId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        if (quiz == null) {
            return Optional.empty();
        }
        synchronized (quiz) {
            Question current = quiz.finished ? null : quiz.current();
            if (current == null) {
                return Optional.empty();
            }
            if (expectedQuestionId != null && !expectedQuestionId.equals(current.getQuestionId())) {
                return Optional.empty();
            }
            boolean closed = scoreManager.closeQuestion(roomId, current.getQuestionId());
            return closed ? Optional.of(current) : Optional.empty();
        }
    }

    /**
     * Closes the current question only if every connected participant has submitted an answer.
     * The room quiz monitor and scoreboard lock keep this transition atomic with quiz restarts.
     */
    public Optional<Question> closeCurrentQuestionIfAllAnswered(String roomId, String expectedQuestionId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        if (quiz == null || expectedQuestionId == null) {
            return Optional.empty();
        }
        synchronized (quiz) {
            Question current = quiz.finished ? null : quiz.current();
            if (current == null || !expectedQuestionId.equals(current.getQuestionId())) {
                return Optional.empty();
            }
            boolean closed = scoreManager.closeQuestionIfAllConnectedAnswered(roomId, expectedQuestionId);
            return closed ? Optional.of(current) : Optional.empty();
        }
    }

    /**
     * Moves to the next question, but only if {@code closedQuestionId} is still the current question
     * and it has already been closed. Two "next" requests for the same question therefore advance
     * exactly once.
     */
    public Advance advanceAfter(String roomId, String closedQuestionId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        if (quiz == null || closedQuestionId == null) {
            return Advance.NOT_ADVANCED;
        }
        synchronized (quiz) {
            Question current = quiz.finished ? null : quiz.current();
            if (current == null || !current.getQuestionId().equals(closedQuestionId)
                    || closedQuestionId.equals(scoreManager.getOpenQuestionId(roomId))) {
                return Advance.NOT_ADVANCED;
            }
            Question next = moveToNext(roomId, quiz);
            return next != null ? new Advance(true, toClient(next), false) : new Advance(true, null, true);
        }
    }

    /** Caller must hold the quiz monitor. Returns the newly opened question or null when finished. */
    private Question moveToNext(String roomId, RoomQuiz quiz) {
        if (quiz.finished) {
            return null;
        }
        quiz.index++;
        Question next = quiz.current();
        if (next == null) {
            quiz.finished = true;
            scoreManager.finishQuiz(roomId);
            log.info("Room {} finished its quiz", roomId);
            return null;
        }
        scoreManager.openQuestion(roomId, next.getQuestionId());
        return next;
    }

    /** 1-based number of the current question, or 0 if not started. */
    public int getQuestionNumber(String roomId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        if (quiz == null) {
            return 0;
        }
        synchronized (quiz) {
            return Math.min(quiz.index + 1, quiz.bank.size());
        }
    }

    public int getTotalQuestions(String roomId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        return quiz != null ? quiz.bank.size() : questionBank.size();
    }

    public String getCategory(String roomId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        return quiz != null ? quiz.category : DEFAULT_CATEGORY;
    }

    // ------------------------------------------------------------------
    // Answers & scores (delegated to ScoreManager)
    // ------------------------------------------------------------------

    /**
     * Evaluates the answer on the server and hands it to {@link ScoreManager}, which decides
     * atomically whether it counts.
     */
    public AnswerOutcome submitAnswerDetailed(String roomId, String playerId, String questionId, int selectedOptionIndex) {
        return submitAnswerDetailed(roomId, playerId, questionId, selectedOptionIndex, 0);
    }

    public AnswerOutcome submitAnswerDetailed(
            String roomId, String playerId, String questionId, int selectedOptionIndex, long deadlineMillis) {
        boolean correct = false;
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        if (quiz != null && questionId != null) {
            for (Question q : quiz.bank) {
                if (q.getQuestionId().equals(questionId)) {
                    Integer correctIdx = q.getCorrectOptionIndex();
                    correct = correctIdx != null && correctIdx == selectedOptionIndex;
                    break;
                }
            }
        }
        AnswerOutcome outcome = scoreManager.submitAnswer(roomId, playerId, questionId, correct, deadlineMillis);
        if (outcome.accepted()) {
            log.info("Player {} in room {} answered {} {} (+{} pts, total {})", playerId, roomId, questionId,
                    outcome.correct() ? "CORRECTLY" : "INCORRECTLY", outcome.pointsAwarded(), outcome.totalScore());
        } else {
            log.info("Player {} in room {} answer for {} REJECTED: {}", playerId, roomId, questionId, outcome.rejectReason());
        }
        return outcome;
    }

    @Override
    public boolean submitAnswer(String roomId, String playerId, String questionId, int selectedOptionIndex) {
        AnswerOutcome outcome = submitAnswerDetailed(roomId, playerId, questionId, selectedOptionIndex);
        return outcome.accepted() && outcome.correct();
    }

    public void registerPlayer(String roomId, String playerId, String username) {
        scoreManager.registerPlayer(roomId, playerId, username);
    }

    public void markDisconnected(String roomId, String playerId) {
        scoreManager.markDisconnected(roomId, playerId);
    }

    /** Removes all per-room quiz and score state when an ephemeral room is terminated. */
    public void removeRoom(String roomId) {
        if (roomId != null) {
            roomQuizzes.remove(key(roomId));
            scoreManager.removeRoom(roomId);
        }
    }

    public int getPlayerScore(String roomId, String playerId) {
        return scoreManager.getScore(roomId, playerId);
    }

    public LeaderboardSnapshot getLeaderboardSnapshot(String roomId) {
        return scoreManager.snapshot(roomId);
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard(String roomId) {
        return scoreManager.snapshot(roomId).entries();
    }

    @Override
    public QuizResult finishGame(String roomId) {
        RoomQuiz quiz = roomQuizzes.get(key(roomId));
        if (quiz != null) {
            synchronized (quiz) {
                quiz.finished = true;
            }
        }
        scoreManager.finishQuiz(roomId);
        return new QuizResult(roomId, getLeaderboard(roomId), System.currentTimeMillis());
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public int getTotalQuestionsCount() {
        return questionBank.size();
    }

    /** Client-safe copy: hides the correct option and shows the fixed point value actually awarded. */
    private static Question toClient(Question q) {
        return new Question(q.getQuestionId(), q.getText(), q.getOptions(), null,
                q.getTimeLimitSeconds(), ScoreManager.POINTS_PER_CORRECT, q.getQuestionNumber(),
                q.getTotalQuestions(), q.getStartTimeMillis(), q.getDeadlineMillis());
    }

    private static String key(String roomId) {
        return roomId == null ? "" : roomId.toUpperCase(Locale.ROOT);
    }
}
