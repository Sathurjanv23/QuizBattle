package com.quizbattle.server.concurrent;

import com.quizbattle.model.Room;
import com.quizbattle.server.game.QuizRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Architectural stub for Member 2: Concurrency & Thread-Safe Room Management.
 *
 * <p>Contract Responsibilities for Member 2:
 * <ul>
 *   <li>Provide lock-free / thread-safe room registry using {@link ConcurrentHashMap}.</li>
 *   <li>Synchronize participant joining, capacity checks, and host migrations.</li>
 *   <li>Ensure thread safety during concurrent answer submissions.</li>
 * </ul>
 * </p>
 */
public class ConcurrentRoomManagerStub implements QuizRoomService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentRoomManagerStub.class);

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    @Override
    public Room createRoom(String hostPlayerId, String roomName, int maxPlayers) {
        log.debug("createRoom stub called for host={}, roomName={}", hostPlayerId, roomName);
        String roomId = "room-" + System.currentTimeMillis();
        Room room = new Room(roomId, roomName, hostPlayerId, maxPlayers, null, List.of());
        rooms.put(roomId, room);
        return room;
    }

    @Override
    public Room joinRoom(String roomId, String playerId) {
        log.debug("joinRoom stub called for roomId={}, player={}", roomId, playerId);
        return rooms.get(roomId);
    }

    @Override
    public void leaveRoom(String roomId, String playerId) {
        log.debug("leaveRoom stub called for roomId={}, player={}", roomId, playerId);
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
