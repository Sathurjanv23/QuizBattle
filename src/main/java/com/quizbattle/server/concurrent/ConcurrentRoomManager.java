package com.quizbattle.server.concurrent;

import com.quizbattle.model.Player;
import com.quizbattle.model.Room;
import com.quizbattle.model.RoomState;
import com.quizbattle.server.game.QuizRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe concurrent multiplayer room manager.
 *
 * <p>Key architectural features:
 * <ul>
 *   <li>Server-authoritative 6-character unique room codes (e.g. {@code QB42KD}).</li>
 *   <li>Per-room fine-grained locking preventing cross-room contention (Room Isolation).</li>
 *   <li>Host migration policy: when the host disconnects or leaves, host privileges
 *       seamlessly transfer to the next remaining participant.</li>
 *   <li>Automatic cleanup of empty ephemeral rooms.</li>
 *   <li>Full backward-compatibility with Member 1's default room.</li>
 * </ul>
 * </p>
 */
public class ConcurrentRoomManager implements QuizRoomService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentRoomManager.class);

    public static final String DEFAULT_ROOM_ID = "room-default";
    public static final String DEFAULT_ROOM_NAME = "General Trivia Arena";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();
    private final Map<String, String> playerToRoom = new ConcurrentHashMap<>();

    public ConcurrentRoomManager() {
        // Initialize default room for quick play / Member 1 baseline compatibility
        Room defaultRoom = new Room(
                DEFAULT_ROOM_ID,
                DEFAULT_ROOM_NAME,
                "server-admin",
                16,
                RoomState.LOBBY,
                new ArrayList<>()
        );
        rooms.put(DEFAULT_ROOM_ID, defaultRoom);
        roomLocks.put(DEFAULT_ROOM_ID, new ReentrantLock());
        log.info("Initialized ConcurrentRoomManager with default room '{}' ({})",
                DEFAULT_ROOM_NAME, DEFAULT_ROOM_ID);
    }

    /**
     * Generates a unique 6-character uppercase alphanumeric room code (e.g. QB42KD).
     */
    public String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < 1000; attempt++) {
            StringBuilder sb = new StringBuilder("QB");
            for (int i = 0; i < 4; i++) {
                int index = RANDOM.nextInt(CODE_ALPHABET.length());
                sb.append(CODE_ALPHABET.charAt(index));
            }
            String code = sb.toString();
            if (!rooms.containsKey(code)) {
                return code;
            }
        }
        return "QB" + System.currentTimeMillis() % 10000;
    }

    @Override
    public Room createRoom(String hostPlayerId, String roomName, int maxPlayers) {
        return createRoom(hostPlayerId, hostPlayerId, roomName, maxPlayers);
    }

    @Override
    public Room createRoom(String hostPlayerId, String hostUsername, String roomName, int maxPlayers) {
        String roomCode = generateUniqueRoomCode();
        String safeRoomName = (roomName != null && !roomName.isBlank()) ? roomName.trim() : "Quiz Room " + roomCode;
        int safeMax = maxPlayers > 0 ? maxPlayers : 8;

        // Leave any existing room
        leaveExistingRoom(hostPlayerId);

        List<Player> initialPlayers = new ArrayList<>();
        initialPlayers.add(new Player(hostPlayerId, hostUsername != null ? hostUsername : hostPlayerId, 0));

        Room newRoom = new Room(
                roomCode,
                safeRoomName,
                hostPlayerId,
                safeMax,
                RoomState.LOBBY,
                initialPlayers
        );

        ReentrantLock lock = new ReentrantLock();
        roomLocks.put(roomCode, lock);
        rooms.put(roomCode, newRoom);
        playerToRoom.put(hostPlayerId, roomCode);

        log.info("Multiplayer room created: code={}, name='{}', hostId={}, hostUsername={}",
                roomCode, safeRoomName, hostPlayerId, hostUsername);
        return newRoom;
    }

    @Override
    public Room joinRoom(String roomId, String playerId) {
        return joinRoom(roomId, playerId, playerId);
    }

    @Override
    public Room joinRoom(String roomIdOrCode, String playerId, String username) {
        String resolvedId = resolveRoomId(roomIdOrCode);
        if (resolvedId == null) {
            log.warn("Attempted to join non-existent room: {}", roomIdOrCode);
            return null;
        }

        ReentrantLock lock = roomLocks.computeIfAbsent(resolvedId, k -> new ReentrantLock());
        lock.lock();
        try {
            Room room = rooms.get(resolvedId);
            if (room == null) {
                return null;
            }

            if (room.isFull()) {
                log.warn("Room {} is full (capacity: {}). Player {} cannot join.",
                        resolvedId, room.getMaxPlayers(), playerId);
                return null;
            }

            // Leave any prior room if different
            String currentRoom = playerToRoom.get(playerId);
            if (currentRoom != null && !currentRoom.equals(resolvedId)) {
                leaveRoom(currentRoom, playerId);
            }

            List<Player> updatedPlayers = new ArrayList<>(room.getPlayers());
            boolean alreadyIn = updatedPlayers.stream().anyMatch(p -> p.getPlayerId().equals(playerId));
            if (!alreadyIn) {
                String safeUsername = (username != null && !username.isBlank()) ? username : playerId;
                updatedPlayers.add(new Player(playerId, safeUsername, 0));
            }

            Room updatedRoom = new Room(
                    room.getRoomId(),
                    room.getRoomName(),
                    room.getHostPlayerId(),
                    room.getMaxPlayers(),
                    room.getState(),
                    updatedPlayers
            );

            rooms.put(resolvedId, updatedRoom);
            playerToRoom.put(playerId, resolvedId);
            log.info("Player {} ({}) joined room {} [total: {}/{}]",
                    playerId, username, resolvedId, updatedPlayers.size(), room.getMaxPlayers());
            return updatedRoom;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void leaveRoom(String roomId, String playerId) {
        String resolvedId = (roomId != null && !roomId.isBlank()) ? resolveRoomId(roomId) : playerToRoom.get(playerId);
        if (resolvedId == null) {
            playerToRoom.remove(playerId);
            return;
        }

        ReentrantLock lock = roomLocks.computeIfAbsent(resolvedId, k -> new ReentrantLock());
        lock.lock();
        try {
            playerToRoom.remove(playerId);
            Room room = rooms.get(resolvedId);
            if (room == null) {
                return;
            }

            List<Player> updatedPlayers = new ArrayList<>(room.getPlayers());
            boolean removed = updatedPlayers.removeIf(p -> p.getPlayerId().equals(playerId));
            if (!removed) {
                return;
            }

            log.info("Player {} left room {} (remaining: {})", playerId, resolvedId, updatedPlayers.size());

            if (updatedPlayers.isEmpty() && !DEFAULT_ROOM_ID.equals(resolvedId)) {
                // Room is empty and not default -> clean up room safely
                rooms.remove(resolvedId);
                roomLocks.remove(resolvedId);
                log.info("Room {} is now empty. Cleaned up room resources.", resolvedId);
                return;
            }

            // If the departing player was host, migrate host to next remaining player
            String newHostId = room.getHostPlayerId();
            if (playerId.equals(room.getHostPlayerId()) && !updatedPlayers.isEmpty()) {
                newHostId = updatedPlayers.getFirst().getPlayerId();
                log.info("Host migration in room {}: {} -> {}", resolvedId, playerId, newHostId);
            }

            Room updatedRoom = new Room(
                    room.getRoomId(),
                    room.getRoomName(),
                    newHostId,
                    room.getMaxPlayers(),
                    room.getState(),
                    updatedPlayers
            );
            rooms.put(resolvedId, updatedRoom);
        } finally {
            lock.unlock();
        }
    }

    private void leaveExistingRoom(String playerId) {
        String existingRoom = playerToRoom.get(playerId);
        if (existingRoom != null) {
            leaveRoom(existingRoom, playerId);
        }
    }

    private String resolveRoomId(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        if (rooms.containsKey(trimmed)) {
            return trimmed;
        }
        // Case-insensitive lookup for room codes
        for (String id : rooms.keySet()) {
            if (id.equalsIgnoreCase(trimmed)) {
                return id;
            }
        }
        return null;
    }

    @Override
    public Optional<Room> findRoomById(String roomId) {
        String resolved = resolveRoomId(roomId);
        return resolved != null ? Optional.ofNullable(rooms.get(resolved)) : Optional.empty();
    }

    @Override
    public List<Room> listAvailableRooms() {
        return new ArrayList<>(rooms.values());
    }

    public boolean roomExists(String roomCode) {
        return resolveRoomId(roomCode) != null;
    }

    public int getActiveRoomCount() {
        return rooms.size();
    }
}
