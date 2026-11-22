package com.quizbattle.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizbattle.model.Player;
import com.quizbattle.model.Question;
import com.quizbattle.model.Room;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.server.concurrent.ConcurrentRoomManager;
import com.quizbattle.server.game.DefaultQuizGameService;
import com.quizbattle.server.game.QuizRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Transport-neutral message dispatcher orchestrating incoming client requests,
 * server-authoritative sessions, multiplayer room lobbies, and question delivery.
 */
public class DefaultServerMessageDispatcher implements ServerMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerMessageDispatcher.class);

    private final QuizRoomService roomService;
    private final DefaultQuizGameService gameService;
    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<MessageType, BiConsumer<ClientSession, Message>> customHandlers = new ConcurrentHashMap<>();

    public DefaultServerMessageDispatcher() {
        this(new ConcurrentRoomManager(), new DefaultQuizGameService());
    }

    public DefaultServerMessageDispatcher(QuizRoomService roomService, DefaultQuizGameService gameService) {
        this.roomService = Objects.requireNonNull(roomService, "roomService must not be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService must not be null");
    }

    @Override
    public void dispatch(ClientSession session, Message message) {
        if (session == null || message == null) {
            return;
        }

        log.debug("Dispatching message type {} from session {}", message.getType(), session.getSessionId());

        // Check custom registered handler first
        BiConsumer<ClientSession, Message> customHandler = customHandlers.get(message.getType());
        if (customHandler != null) {
            customHandler.accept(session, message);
            return;
        }

        switch (message.getType()) {
            case CONNECT -> handleConnect(session, message);
            case CREATE_ROOM -> handleCreateRoom(session, message);
            case JOIN_ROOM -> handleJoinRoom(session, message);
            case LEAVE_ROOM -> handleLeaveRoom(session, message);
            case ANSWER -> handleAnswer(session, message);
            case START_QUIZ -> handleStartQuiz(session, message);
            case PING -> handlePing(session, message);
            default -> {
                log.warn("Unhandled message type: {}", message.getType());
                session.send(Message.error(message.getRequestId(), "UNSUPPORTED_TYPE",
                        "Message type " + message.getType() + " is not supported"));
            }
        }
    }

    private void handleConnect(ClientSession session, Message message) {
        String username = "Anonymous";
        JsonNode payload = message.getPayload();
        if (payload != null && payload.has("username")) {
            String candidate = payload.get("username").asText().trim();
            if (!candidate.isBlank()) {
                username = candidate;
            }
        }

        String playerId = "usr-" + session.getSessionId().substring(0, 8);
        session.setPlayerId(playerId);
        session.setUsername(username);

        log.info("Client authenticated: username={}, playerId={}, session={}",
                username, playerId, session.getSessionId());

        Map<String, Object> respPayload = Map.of(
                "playerId", playerId,
                "username", username,
                "protocolVersion", "2.0"
        );
        session.send(Message.request(MessageType.CONNECTED, message.getRequestId(), respPayload));
    }

    private void handleCreateRoom(ClientSession session, Message message) {
        if (!session.isAuthenticated()) {
            session.send(Message.error(message.getRequestId(), "UNAUTHORIZED", "Must authenticate via CONNECT first"));
            return;
        }

        String roomName = "Quiz Room";
        int maxPlayers = 8;

        JsonNode payload = message.getPayload();
        if (payload != null) {
            if (payload.has("roomName")) {
                String candidate = payload.get("roomName").asText().trim();
                if (!candidate.isBlank()) {
                    roomName = candidate;
                }
            }
            if (payload.has("maxPlayers")) {
                int candidate = payload.get("maxPlayers").asInt(8);
                if (candidate > 0) {
                    maxPlayers = candidate;
                }
            }
        }

        Room room = roomService.createRoom(session.getPlayerId(), session.getUsername(), roomName, maxPlayers);
        session.setCurrentRoomId(room.getRoomId());

        Map<String, Object> respPayload = new LinkedHashMap<>();
        respPayload.put("roomId", room.getRoomId());
        respPayload.put("roomCode", room.getRoomId());
        respPayload.put("roomName", room.getRoomName());
        respPayload.put("maxPlayers", room.getMaxPlayers());
        respPayload.put("hostPlayerId", room.getHostPlayerId());

        session.send(Message.request(MessageType.ROOM_CREATED, message.getRequestId(), respPayload));
        broadcastRoomState(room.getRoomId());
    }

    private void handleJoinRoom(ClientSession session, Message message) {
        if (!session.isAuthenticated()) {
            session.send(Message.error(message.getRequestId(), "UNAUTHORIZED", "Must authenticate via CONNECT first"));
            return;
        }

        String targetRoomId = null;
        JsonNode payload = message.getPayload();
        if (payload != null) {
            if (payload.has("roomCode")) {
                String code = payload.get("roomCode").asText().trim();
                if (!code.isBlank()) {
                    targetRoomId = code;
                }
            }
            if (targetRoomId == null && payload.has("roomId")) {
                String id = payload.get("roomId").asText().trim();
                if (!id.isBlank()) {
                    targetRoomId = id;
                }
            }
        }

        // If no room specified, join default room
        if (targetRoomId == null) {
            targetRoomId = ConcurrentRoomManager.DEFAULT_ROOM_ID;
        }

        // Validate room existence
        Optional<Room> existingOpt = roomService.findRoomById(targetRoomId);
        if (existingOpt.isEmpty()) {
            log.warn("Join rejected: room '{}' not found for session {}", targetRoomId, session.getSessionId());
            session.send(Message.error(message.getRequestId(), "ROOM_NOT_FOUND", "Room '" + targetRoomId + "' does not exist"));
            return;
        }

        Room existingRoom = existingOpt.get();
        if (existingRoom.isFull()) {
            log.warn("Join rejected: room '{}' is at capacity ({})", targetRoomId, existingRoom.getMaxPlayers());
            session.send(Message.error(message.getRequestId(), "ROOM_FULL", "Room '" + targetRoomId + "' is full"));
            return;
        }

        Room room = roomService.joinRoom(targetRoomId, session.getPlayerId(), session.getUsername());
        if (room == null) {
            session.send(Message.error(message.getRequestId(), "JOIN_FAILED", "Failed to join room '" + targetRoomId + "'"));
            return;
        }

        session.setCurrentRoomId(room.getRoomId());

        // Confirm join to client
        Map<String, Object> respPayload = Map.of(
                "roomId", room.getRoomId(),
                "roomCode", room.getRoomId(),
                "roomName", room.getRoomName()
        );
        session.send(Message.request(MessageType.ROOM_JOINED, message.getRequestId(), respPayload));

        // Broadcast updated room state to all room participants
        broadcastRoomState(room.getRoomId());

        // Send current active question for default room (quick play) or active game
        if (ConcurrentRoomManager.DEFAULT_ROOM_ID.equalsIgnoreCase(room.getRoomId()) || room.getState() == com.quizbattle.model.RoomState.IN_PROGRESS) {
            Optional<Question> questionOpt = gameService.getCurrentQuestion(room.getRoomId());
            questionOpt.ifPresent(question -> session.send(Message.event(MessageType.QUESTION, question)));
        }
    }

    private void handleLeaveRoom(ClientSession session, Message message) {
        if (!session.isAuthenticated()) {
            session.send(Message.error(message.getRequestId(), "UNAUTHORIZED", "Must authenticate via CONNECT first"));
            return;
        }

        String roomId = session.getCurrentRoomId();
        if (roomId == null) {
            session.send(Message.error(message.getRequestId(), "NOT_IN_ROOM", "Player is not currently in any room"));
            return;
        }

        roomService.leaveRoom(roomId, session.getPlayerId());
        session.setCurrentRoomId(null);

        session.send(Message.request(MessageType.ROOM_LEFT, message.getRequestId(), Map.of(
                "roomId", roomId,
                "roomCode", roomId
        )));

        // Broadcast updated room state to remaining participants
        broadcastRoomState(roomId);
    }

    private void handleAnswer(ClientSession session, Message message) {
        if (!session.isAuthenticated()) {
            session.send(Message.error(message.getRequestId(), "UNAUTHORIZED", "Must authenticate via CONNECT first"));
            return;
        }
        String roomId = session.getCurrentRoomId();
        if (roomId == null) {
            session.send(Message.error(message.getRequestId(), "NOT_IN_ROOM", "Player has not joined a room yet"));
            return;
        }

        JsonNode payload = message.getPayload();
        if (payload == null || !payload.has("questionId") || !payload.has("selectedOptionIndex")) {
            session.send(Message.error(message.getRequestId(), "BAD_PAYLOAD", "Missing questionId or selectedOptionIndex"));
            return;
        }

        String questionId = payload.get("questionId").asText();
        int selectedOptionIndex = payload.get("selectedOptionIndex").asInt();

        boolean isCorrect = gameService.submitAnswer(roomId, session.getPlayerId(), questionId, selectedOptionIndex);
        int totalScore = gameService.getPlayerScore(roomId, session.getPlayerId());

        Map<String, Object> resultPayload = Map.of(
                "questionId", questionId,
                "correct", isCorrect,
                "pointsAwarded", isCorrect ? 100 : 0,
                "currentTotalScore", totalScore
        );
        session.send(Message.request(MessageType.ANSWER_RESULT, message.getRequestId(), resultPayload));
    }

    private void handleStartQuiz(ClientSession session, Message message) {
        String roomId = session.getCurrentRoomId();
        if (roomId == null) {
            session.send(Message.error(message.getRequestId(), "NOT_IN_ROOM", "Cannot advance question outside a room"));
            return;
        }

        // Validate host permission (allow server-admin or room host)
        Optional<Room> roomOpt = roomService.findRoomById(roomId);
        if (roomOpt.isPresent()) {
            Room room = roomOpt.get();
            if (!session.getPlayerId().equals(room.getHostPlayerId()) && !"server-admin".equals(room.getHostPlayerId())) {
                session.send(Message.error(message.getRequestId(), "FORBIDDEN", "Only the room host can start the quiz"));
                return;
            }
        }

        Optional<Question> nextQ = gameService.advanceQuestion(roomId);
        if (nextQ.isPresent()) {
            broadcastToRoom(roomId, Message.event(MessageType.QUESTION, nextQ.get()));
        }
    }

    private void handlePing(ClientSession session, Message message) {
        session.send(Message.pong(message.getRequestId()));
    }

    /**
     * Constructs and broadcasts an authoritative ROOM_STATE message to all participants in a room.
     */
    public void broadcastRoomState(String roomId) {
        if (roomId == null) return;
        Optional<Room> roomOpt = roomService.findRoomById(roomId);
        if (roomOpt.isEmpty()) {
            return;
        }

        Room room = roomOpt.get();
        List<Map<String, Object>> playersList = new ArrayList<>();
        for (Player p : room.getPlayers()) {
            boolean isHost = p.getPlayerId().equals(room.getHostPlayerId());
            playersList.add(Map.of(
                    "playerId", p.getPlayerId(),
                    "username", p.getUsername(),
                    "score", p.getScore(),
                    "isHost", isHost,
                    "role", isHost ? "HOST" : "PLAYER"
            ));
        }

        String hostUsername = room.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(room.getHostPlayerId()))
                .map(Player::getUsername)
                .findFirst()
                .orElse(room.getHostPlayerId());

        Map<String, Object> statePayload = Map.of(
                "roomId", room.getRoomId(),
                "roomCode", room.getRoomId(),
                "roomName", room.getRoomName(),
                "host", hostUsername,
                "hostPlayerId", room.getHostPlayerId(),
                "state", room.getState().name(),
                "playerCount", room.getPlayerCount(),
                "players", playersList
        );

        broadcastToRoom(roomId, Message.event(MessageType.ROOM_STATE, statePayload));
    }

    /**
     * Broadcasts a protocol message strictly to all connected sessions in the specified room.
     * Enforces strict room isolation.
     */
    public void broadcastToRoom(String roomId, Message message) {
        if (roomId == null || message == null) return;
        for (ClientSession session : activeSessions.values()) {
            if (roomId.equalsIgnoreCase(session.getCurrentRoomId()) && session.isConnected()) {
                session.send(message);
            }
        }
    }

    @Override
    public void registerHandler(MessageType type, BiConsumer<ClientSession, Message> handler) {
        customHandlers.put(type, handler);
    }

    @Override
    public void onSessionConnected(ClientSession session) {
        activeSessions.put(session.getSessionId(), session);
        log.info("Session connected: id={}", session.getSessionId());
    }

    @Override
    public void onSessionDisconnected(ClientSession session) {
        activeSessions.remove(session.getSessionId());
        String roomId = session.getCurrentRoomId();
        if (roomId != null && session.getPlayerId() != null) {
            roomService.leaveRoom(roomId, session.getPlayerId());
            broadcastRoomState(roomId);
        }
        log.info("Session disconnected: id={}", session.getSessionId());
    }

    public QuizRoomService getRoomService() {
        return roomService;
    }

    public DefaultQuizGameService getGameService() {
        return gameService;
    }

    public Map<String, ClientSession> getActiveSessions() {
        return activeSessions;
    }
}
