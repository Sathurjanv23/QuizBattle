package com.quizbattle.server.game;

import com.quizbattle.model.Question;
import com.quizbattle.server.game.DefaultQuizGameService.Advance;
import com.quizbattle.server.sync.ScoreManager.RejectReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Member 5: the game service delegates all scoring to ScoreManager and never double-advances.
 */
@DisplayName("Member 5: quiz progression and score delegation")
class QuizProgressionTest {

    private static final String ROOM = "QB0001";

    @Test
    @DisplayName("Close then advance moves exactly one question; repeated advance for the same id is ignored")
    void closeAndAdvanceOnce() {
        DefaultQuizGameService game = new DefaultQuizGameService();
        Question q1 = game.startQuiz(ROOM, "Java", Map.of("p1", "Alice")).orElseThrow();
        assertEquals("java-101", q1.getQuestionId());
        assertNull(q1.getCorrectOptionIndex(), "client copy hides the answer");
        assertEquals(10, q1.getPointValue());
        assertEquals(20, q1.getTimeLimitSeconds());
        assertEquals(10, game.getTotalQuestions(ROOM));

        assertFalse(game.advanceAfter(ROOM, "java-101").advanced(), "cannot advance an open question");
        assertTrue(game.closeCurrentQuestion(ROOM, "java-101").isPresent());
        assertTrue(game.closeCurrentQuestion(ROOM, "java-101").isEmpty(), "second close is a no-op");

        Advance a = game.advanceAfter(ROOM, "java-101");
        assertTrue(a.advanced());
        assertEquals("java-102", a.nextQuestion().getQuestionId());
        assertFalse(game.advanceAfter(ROOM, "java-101").advanced(), "stale advance ignored");
        assertEquals(2, game.getQuestionNumber(ROOM));
    }

    @Test
    @DisplayName("Closing and advancing past the last question finishes the quiz")
    void finishesAfterLastQuestion() {
        DefaultQuizGameService game = new DefaultQuizGameService();
        Optional<Question> q = game.startQuiz(ROOM, "Networking", Map.of("p1", "Alice"));
        int seen = 0;
        Advance last = null;
        while (q.isPresent()) {
            seen++;
            String id = q.get().getQuestionId();
            game.closeCurrentQuestion(ROOM, id);
            last = game.advanceAfter(ROOM, id);
            q = Optional.ofNullable(last.nextQuestion());
        }
        assertEquals(game.getTotalQuestions(ROOM), seen);
        assertTrue(last.finished());
        assertFalse(game.isQuizActive(ROOM));
        assertTrue(game.getLeaderboardSnapshot(ROOM).finished());
    }

    @Test
    @DisplayName("Game service scores through ScoreManager: one source of truth")
    void delegatesScoring() {
        DefaultQuizGameService game = new DefaultQuizGameService();
        Question q1 = game.startQuiz(ROOM, "Java", Map.of("p1", "Alice")).orElseThrow();
        var first = game.submitAnswerDetailed(ROOM, "p1", q1.getQuestionId(), 0);
        assertTrue(first.accepted());
        var second = game.submitAnswerDetailed(ROOM, "p1", q1.getQuestionId(), 1);
        assertEquals(RejectReason.DUPLICATE, second.rejectReason());
        assertEquals(first.totalScore(), game.getPlayerScore(ROOM, "p1"));
        assertEquals(game.getScoreManager().getScore(ROOM, "p1"), game.getPlayerScore(ROOM, "p1"));
        assertEquals(1, game.getLeaderboard(ROOM).size());
    }
}
