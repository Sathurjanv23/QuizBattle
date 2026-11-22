package com.quizbattle.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain model representing a Quiz room / lobby.
 */
public class Room {

    private final String roomId;
    private final String roomName;
    private final String hostPlayerId;
    private final int maxPlayers;
    private final RoomState state;
    private final List<Player> players;

    @JsonCreator
    public Room(
            @JsonProperty("roomId") String roomId,
            @JsonProperty("roomName") String roomName,
            @JsonProperty("hostPlayerId") String hostPlayerId,
            @JsonProperty("maxPlayers") int maxPlayers,
            @JsonProperty("state") RoomState state,
            @JsonProperty("players") List<Player> players) {
        this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
        this.roomName = Objects.requireNonNull(roomName, "roomName must not be null");
        this.hostPlayerId = Objects.requireNonNull(hostPlayerId, "hostPlayerId must not be null");
        this.maxPlayers = maxPlayers;
        this.state = state != null ? state : RoomState.LOBBY;
        this.players = players != null ? Collections.unmodifiableList(players) : Collections.emptyList();
    }

    public String getRoomId() {
        return roomId;
    }

    @JsonProperty("roomCode")
    public String getRoomCode() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getHostPlayerId() {
        return hostPlayerId;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public RoomState getState() {
        return state;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room room)) return false;
        return Objects.equals(roomId, room.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId);
    }

    @Override
    public String toString() {
        return "Room{" +
                "roomId='" + roomId + '\'' +
                ", roomName='" + roomName + '\'' +
                ", hostPlayerId='" + hostPlayerId + '\'' +
                ", maxPlayers=" + maxPlayers +
                ", state=" + state +
                ", playerCount=" + players.size() +
                '}';
    }
}
