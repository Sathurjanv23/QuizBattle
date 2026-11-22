package com.quizbattle.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Domain model representing a QuizBattle player.
 *
 * <p>Security note: Scores and credentials are authoritative only on the server.
 * The client UI renders this data as received from the server.</p>
 */
public class Player {

    private final String playerId;
    private final String username;
    private final int score;

    @JsonCreator
    public Player(
            @JsonProperty("playerId") String playerId,
            @JsonProperty("username") String username,
            @JsonProperty("score") int score) {
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.score = score;
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

    public Player withScore(int newScore) {
        return new Player(this.playerId, this.username, newScore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player player)) return false;
        return Objects.equals(playerId, player.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }

    @Override
    public String toString() {
        return "Player{" +
                "playerId='" + playerId + '\'' +
                ", username='" + username + '\'' +
                ", score=" + score +
                '}';
    }
}
