package com.quizbattle.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Domain model representing a single entry in the quiz room leaderboard.
 */
public class LeaderboardEntry implements Comparable<LeaderboardEntry> {

    private final int rank;
    private final String playerId;
    private final String username;
    private final int score;
    private final int correctCount;
    private final boolean connected;

    public LeaderboardEntry(int rank, String playerId, String username, int score, int correctCount) {
        this(rank, playerId, username, score, correctCount, Boolean.TRUE);
    }

    @JsonCreator
    public LeaderboardEntry(
            @JsonProperty("rank") int rank,
            @JsonProperty("playerId") String playerId,
            @JsonProperty("username") String username,
            @JsonProperty("score") int score,
            @JsonProperty("correctCount") int correctCount,
            @JsonProperty("connected") Boolean connected) {
        this.rank = rank;
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.score = score;
        this.correctCount = correctCount;
        this.connected = connected == null || connected;
    }

    public int getRank() {
        return rank;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getUsername() {
        return username;
    }

    public int getScore() {
        return score;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    /** False when the player disconnected or left; their score is still kept. */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Stable leaderboard order: score descending, then username ascending (case-insensitive),
     * then playerId so two players with the same name still order deterministically.
     */
    @Override
    public int compareTo(LeaderboardEntry o) {
        int scoreCompare = Integer.compare(o.score, this.score);
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        int nameCompare = String.CASE_INSENSITIVE_ORDER.compare(this.username, o.username);
        if (nameCompare != 0) {
            return nameCompare;
        }
        return this.playerId.compareTo(o.playerId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaderboardEntry that)) return false;
        return rank == that.rank &&
                score == that.score &&
                correctCount == that.correctCount &&
                connected == that.connected &&
                Objects.equals(playerId, that.playerId) &&
                Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, playerId, username, score, correctCount, connected);
    }

    @Override
    public String toString() {
        return "LeaderboardEntry{" +
                "rank=" + rank +
                ", playerId='" + playerId + '\'' +
                ", username='" + username + '\'' +
                ", score=" + score +
                ", correctCount=" + correctCount +
                ", connected=" + connected +
                '}';
    }
}
