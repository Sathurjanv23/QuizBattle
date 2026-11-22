package com.quizbattle.server.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizbattle.model.LeaderboardEntry;
import com.quizbattle.model.Question;
import com.quizbattle.model.QuizResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transport-neutral game logic engine for QuizBattle.
 *
 * <p>Loads 5 fixed questions from JSON resources, manages per-room question pointers,
 * validates answers against server-held correct option indices, and records provisional player scores.</p>
 */
public class DefaultQuizGameService implements QuizGameService {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuizGameService.class);

    private final List<Question> questionBank;
    private final Map<String, AtomicInteger> roomQuestionIndex = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicInteger>> roomPlayerScores = new ConcurrentHashMap<>();

    public DefaultQuizGameService() {
        this("/questions/sample_questions.json");
    }

    public DefaultQuizGameService(String resourcePath) {
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
                    0, 20, 100));
        }

        this.questionBank = Collections.unmodifiableList(loaded);
    }

    @Override
    public void startGame(String roomId, String initiatedByPlayerId) {
        roomQuestionIndex.computeIfAbsent(roomId, k -> new AtomicInteger(0)).set(0);
        roomPlayerScores.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        log.info("Game started in room {} by player {}", roomId, initiatedByPlayerId);
    }

    @Override
    public Optional<Question> nextQuestion(String roomId) {
        if (questionBank.isEmpty()) {
            return Optional.empty();
        }
        AtomicInteger idx = roomQuestionIndex.computeIfAbsent(roomId, k -> new AtomicInteger(0));
        int current = idx.getAndIncrement() % questionBank.size();
        return Optional.of(questionBank.get(current).forClient());
    }

    public Optional<Question> getCurrentQuestion(String roomId) {
        if (questionBank.isEmpty()) {
            return Optional.empty();
        }
        AtomicInteger idx = roomQuestionIndex.computeIfAbsent(roomId, k -> new AtomicInteger(0));
        int current = Math.abs(idx.get()) % questionBank.size();
        return Optional.of(questionBank.get(current).forClient());
    }

    public Optional<Question> advanceQuestion(String roomId) {
        if (questionBank.isEmpty()) {
            return Optional.empty();
        }
        AtomicInteger idx = roomQuestionIndex.computeIfAbsent(roomId, k -> new AtomicInteger(0));
        int next = idx.incrementAndGet() % questionBank.size();
        return Optional.of(questionBank.get(next).forClient());
    }

    @Override
    public boolean submitAnswer(String roomId, String playerId, String questionId, int selectedOptionIndex) {
        AtomicInteger idx = roomQuestionIndex.computeIfAbsent(roomId, k -> new AtomicInteger(0));
        int currentIdx = Math.abs(idx.get()) % questionBank.size();
        Question activeQuestion = questionBank.get(currentIdx);

        boolean correct = false;
        if (activeQuestion.getQuestionId().equals(questionId)) {
            Integer correctIdx = activeQuestion.getCorrectOptionIndex();
            correct = correctIdx != null && correctIdx == selectedOptionIndex;
        } else {
            // Also search question bank in case of slight delay
            for (Question q : questionBank) {
                if (q.getQuestionId().equals(questionId)) {
                    Integer correctIdx = q.getCorrectOptionIndex();
                    correct = correctIdx != null && correctIdx == selectedOptionIndex;
                    break;
                }
            }
        }

        if (correct) {
            Map<String, AtomicInteger> scores = roomPlayerScores.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
            scores.computeIfAbsent(playerId, k -> new AtomicInteger(0)).addAndGet(activeQuestion.getPointValue());
            log.info("Player {} in room {} answered question {} CORRECTLY (+{} pts)",
                    playerId, roomId, questionId, activeQuestion.getPointValue());
        } else {
            log.info("Player {} in room {} answered question {} INCORRECTLY (option {})",
                    playerId, roomId, questionId, selectedOptionIndex);
        }

        return correct;
    }

    public int getPlayerScore(String roomId, String playerId) {
        Map<String, AtomicInteger> scores = roomPlayerScores.get(roomId);
        if (scores != null) {
            AtomicInteger score = scores.get(playerId);
            if (score != null) {
                return score.get();
            }
        }
        return 0;
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard(String roomId) {
        Map<String, AtomicInteger> scores = roomPlayerScores.get(roomId);
        if (scores == null || scores.isEmpty()) {
            return Collections.emptyList();
        }
        List<LeaderboardEntry> list = new ArrayList<>();
        scores.forEach((pid, score) -> {
            list.add(new LeaderboardEntry(1, pid, pid, score.get(), 1));
        });
        list.sort(null);
        return list;
    }

    @Override
    public QuizResult finishGame(String roomId) {
        return new QuizResult(roomId, getLeaderboard(roomId), System.currentTimeMillis());
    }

    public int getTotalQuestionsCount() {
        return questionBank.size();
    }
}
