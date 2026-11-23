package com.quizbattle.model;

import com.quizbattle.server.game.DefaultQuizGameService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain Models Smoke Tests")
class ModelSmokeTest {

    @Test
    @DisplayName("Player creation and score immutability")
    void testPlayerModel() {
        Player player = new Player("p-1", "Alice", 0);
        assertEquals("p-1", player.getPlayerId());
        assertEquals("Alice", player.getUsername());
        assertEquals(0, player.getScore());

        Player updated = player.withScore(150);
        assertEquals(150, updated.getScore());
        assertEquals(0, player.getScore(), "Original player score must be immutable");
    }

    @Test
    @DisplayName("Question sanitization for client distribution")
    void testQuestionClientSanitization() {
        Question serverQuestion = new Question(
                "q-1",
                "What port is default for QuizBattle TCP?",
                List.of("5000", "8080", "3000"),
                0, // correct index
                15,
                100
        );

        assertEquals(0, serverQuestion.getCorrectOptionIndex());

        Question clientQuestion = serverQuestion.forClient();
        assertNull(clientQuestion.getCorrectOptionIndex(),
                "Client view must NOT contain the correct answer index before submission");
        assertEquals("q-1", clientQuestion.getQuestionId());
        assertEquals(3, clientQuestion.getOptions().size());
    }

    @Test
    @DisplayName("Leaderboard ordering sorts by score descending")
    void testLeaderboardSorting() {
        LeaderboardEntry e1 = new LeaderboardEntry(1, "p1", "Alice", 100, 2);
        LeaderboardEntry e2 = new LeaderboardEntry(2, "p2", "Bob", 250, 3);

        List<LeaderboardEntry> list = new java.util.ArrayList<>(List.of(e1, e2));
        list.sort(null);

        assertEquals("p2", list.get(0).getPlayerId(), "Bob with score 250 must sort ahead of Alice with score 100");
    }

    @Test
    @DisplayName("Quiz category loads a category-specific question bank")
    void testQuizCategorySelection() {
        DefaultQuizGameService gameService = new DefaultQuizGameService();
        gameService.setCategory("room-java", "Java");

        Optional<Question> question = gameService.advanceQuestion("room-java");

        assertTrue(question.isPresent());
        assertEquals("java-101", question.get().getQuestionId());
    }
}
