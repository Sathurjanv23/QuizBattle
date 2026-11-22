package com.quizbattle.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain model representing final quiz outcome and standings for a room.
 */
public class QuizResult {

    private final String roomId;
    private final List<LeaderboardEntry> finalStandings;
    private final long finishedAt;

    @JsonCreator
    public QuizResult(
            @JsonProperty("roomId") String roomId,
            @JsonProperty("finalStandings") List<LeaderboardEntry> finalStandings,
            @JsonProperty("finishedAt") long finishedAt) {
        this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
        this.finalStandings = finalStandings != null ? Collections.unmodifiableList(finalStandings) : Collections.emptyList();
        this.finishedAt = finishedAt;
    }

    public String getRoomId() {
        return roomId;
    }

    public List<LeaderboardEntry> getFinalStandings() {
        return finalStandings;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuizResult that)) return false;
        return finishedAt == that.finishedAt &&
                Objects.equals(roomId, that.roomId) &&
                Objects.equals(finalStandings, that.finalStandings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, finalStandings, finishedAt);
    }

    @Override
    public String toString() {
        return "QuizResult{" +
                "roomId='" + roomId + '\'' +
                ", standingsCount=" + finalStandings.size() +
                ", finishedAt=" + finishedAt +
                '}';
    }
}
