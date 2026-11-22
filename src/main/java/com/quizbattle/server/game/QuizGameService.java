package com.quizbattle.server.game;

import com.quizbattle.model.LeaderboardEntry;
import com.quizbattle.model.Question;
import com.quizbattle.model.QuizResult;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for orchestrating active quiz rounds, question dispatch,
 * answer evaluation, and scoring.
 */
public interface QuizGameService {

    /**
     * Starts a quiz match in the specified room.
     *
     * @param roomId identifier of the room
     * @param initiatedByPlayerId must be the room host
     */
    void startGame(String roomId, String initiatedByPlayerId);

    /**
     * Advances to the next question in the quiz.
     */
    Optional<Question> nextQuestion(String roomId);

    /**
     * Submits an answer for the active question.
     *
     * @param roomId target room
     * @param playerId answering player
     * @param questionId current question ID
     * @param selectedOptionIndex index chosen by player
     * @return true if answer is correct, false otherwise
     */
    boolean submitAnswer(String roomId, String playerId, String questionId, int selectedOptionIndex);

    /**
     * Retrieves the current room leaderboard ordered by score descending.
     */
    List<LeaderboardEntry> getLeaderboard(String roomId);

    /**
     * Finalizes the match and computes final standings.
     */
    QuizResult finishGame(String roomId);
}
