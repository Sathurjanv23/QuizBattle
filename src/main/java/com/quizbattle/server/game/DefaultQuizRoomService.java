package com.quizbattle.server.game;

import com.quizbattle.model.Player;
import com.quizbattle.model.Room;
import com.quizbattle.model.RoomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link QuizRoomService}.
 * Pre-populates a default room for simple immediate gameplay.
 */
public class DefaultQuizRoomService implements QuizRoomService {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuizRoomService.class);

    public static final String DEFAULT_ROOM_ID = "room-default";
    public static final String DEFAULT_ROOM_NAME = "General Trivia Arena";

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> playerToRoom = new ConcurrentHashMap<>();

    public DefaultQuizRoomService() {
        // Pre-create the default room
        Room defaultRoom = new Room(
                DEFAULT_ROOM_ID,
                DEFAULT_ROOM_NAME,
                "server-admin",
                10,
                RoomState.LOBBY,
                new ArrayList<>()
        );
        rooms.put(DEFAULT_ROOM_ID, defaultRoom);
        log.info("Initialized DefaultQuizRoomService with default room '{}' ({})",
                DEFAULT_ROOM_NAME, DEFAULT_ROOM_ID);
    }

    @Override
    public synchronized Room createRoom(String hostPlayerId, String roomName, int maxPlayers) {
        String roomId = "room-" + System.currentTimeMillis();
        Room room = new Room(roomId, roomName, hostPlayerId, maxPlayers, RoomState.LOBBY, new ArrayList<>());
        rooms.put(roomId, room);
        log.info("Room created: id={}, name={}, host={}", roomId, roomName, hostPlayerId);
        return room;
    }

    @Override
    public synchronized Room joinRoom(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            // Fall back to default room if requested room doesn't exist
            room = rooms.get(DEFAULT_ROOM_ID);
            roomId = DEFAULT_ROOM_ID;
        }

        // Leave any prior room
        leaveRoom(roomId, playerId);

        List<Player> updatedPlayers = new ArrayList<>(room.getPlayers());
        boolean alreadyIn = updatedPlayers.stream().anyMatch(p -> p.getPlayerId().equals(playerId));
        if (!alreadyIn) {
            updatedPlayers.add(new Player(playerId, playerId, 0));
        }

        Room updatedRoom = new Room(
                room.getRoomId(),
                room.getRoomName(),
                room.getHostPlayerId(),
                room.getMaxPlayers(),
                room.getState(),
                updatedPlayers
        );
        rooms.put(roomId, updatedRoom);
        playerToRoom.put(playerId, roomId);
        log.info("Player {} joined room {} (total players: {})", playerId, roomId, updatedPlayers.size());
        return updatedRoom;
    }

    @Override
    public synchronized void leaveRoom(String roomId, String playerId) {
        String currentRoom = playerToRoom.remove(playerId);
        String targetRoomId = (roomId != null && !roomId.isBlank()) ? roomId : currentRoom;
        if (targetRoomId == null) {
            return;
        }

        Room room = rooms.get(targetRoomId);
        if (room != null) {
            List<Player> updatedPlayers = new ArrayList<>(room.getPlayers());
            updatedPlayers.removeIf(p -> p.getPlayerId().equals(playerId));
            Room updatedRoom = new Room(
                    room.getRoomId(),
                    room.getRoomName(),
                    room.getHostPlayerId(),
                    room.getMaxPlayers(),
                    room.getState(),
                    updatedPlayers
            );
            rooms.put(targetRoomId, updatedRoom);
            log.info("Player {} left room {}", playerId, targetRoomId);
        }
    }

    @Override
    public Optional<Room> findRoomById(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    @Override
    public List<Room> listAvailableRooms() {
        return new ArrayList<>(rooms.values());
    }
}
