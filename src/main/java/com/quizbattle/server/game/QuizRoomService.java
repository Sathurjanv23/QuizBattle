package com.quizbattle.server.game;

import com.quizbattle.model.Room;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for managing quiz battle rooms and participant memberships.
 */
public interface QuizRoomService {

    /**
     * Creates a new game room with the given host.
     *
     * @param hostPlayerId authoritative player ID of the host
     * @param roomName human-readable name of the room
     * @param maxPlayers maximum player capacity
     * @return newly created Room instance
     */
    Room createRoom(String hostPlayerId, String roomName, int maxPlayers);

    /**
     * Creates a new game room with host player ID and username.
     */
    default Room createRoom(String hostPlayerId, String hostUsername, String roomName, int maxPlayers) {
        return createRoom(hostPlayerId, roomName, maxPlayers);
    }

    /**
     * Joins an existing room.
     *
     * @param roomId room identifier
     * @param playerId player identifier
     * @return updated Room instance
     */
    Room joinRoom(String roomId, String playerId);

    /**
     * Joins an existing room with player ID and username.
     */
    default Room joinRoom(String roomId, String playerId, String username) {
        return joinRoom(roomId, playerId);
    }

    /**
     * Removes a player from a room.
     */
    void leaveRoom(String roomId, String playerId);

    /**
     * Finds a room by ID.
     */
    Optional<Room> findRoomById(String roomId);

    /**
     * Lists all publicly discoverable rooms currently in LOBBY state.
     */
    List<Room> listAvailableRooms();
}
