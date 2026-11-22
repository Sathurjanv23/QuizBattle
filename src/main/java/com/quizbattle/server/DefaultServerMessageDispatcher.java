package com.quizbattle.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizbattle.model.Question;
import com.quizbattle.model.Room;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.server.game.DefaultQuizGameService;
import com.quizbattle.server.game.DefaultQuizRoomService;
import com.quizbattle.server.game.QuizRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Transport-neutral message dispatcher orchestrating incoming client requests,
 * room memberships, and trivia question delivery.
 */
public class DefaultServerMessageDispatcher implements ServerMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerMessageDispatcher.class);

    private final QuizRoomService roomService;
    private final DefaultQuizGameService gameService;
    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<MessageType, BiConsumer<ClientSession, Message>> customHandlers = new ConcurrentHashMap<>();

    public DefaultServerMessageDispatcher() {
        this(new DefaultQuizRoomService(), new DefaultQuizGameService());
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
            case JOIN_ROOM -> handleJoinRoom(session, message);
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

    private void handleJoinRoom(ClientSession session, Message message) {
        if (!session.isAuthenticated()) {
            session.send(Message.error(message.getRequestId(), "UNAUTHORIZED", "Must authenticate via CONNECT first"));
            return;
        }

        String targetRoomId = DefaultQuizRoomService.DEFAULT_ROOM_ID;
        JsonNode payload = message.getPayload();
        if (payload != null && payload.has("roomId")) {
            String requested = payload.get("roomId").asText().trim();
            if (!requested.isBlank()) {
                targetRoomId = requested;
            }
        }

        Room room = roomService.joinRoom(targetRoomId, session.getPlayerId());
        session.setCurrentRoomId(room.getRoomId());

        // Confirm join to client
        Map<String, Object> respPayload = Map.of(
                "roomId", room.getRoomId(),
                "roomName", room.getRoomName()
        );
        session.send(Message.request(MessageType.ROOM_JOINED, message.getRequestId(), respPayload));

        // Broadcast room state to room participants
        broadcastToRoom(room.getRoomId(), Message.event(MessageType.ROOM_STATE, Map.of(
                "roomId", room.getRoomId(),
                "roomName", room.getRoomName(),
                "playerCount", room.getPlayerCount()
        )));

        // Send the current active question to the newly joined player
        Optional<Question> questionOpt = gameService.getCurrentQuestion(room.getRoomId());
        questionOpt.ifPresent(question -> session.send(Message.event(MessageType.QUESTION, question)));
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

        Optional<Question> nextQ = gameService.advanceQuestion(roomId);
        if (nextQ.isPresent()) {
            broadcastToRoom(roomId, Message.event(MessageType.QUESTION, nextQ.get()));
        }
    }

    private void handlePing(ClientSession session, Message message) {
        session.send(Message.pong(message.getRequestId()));
    }

    public void broadcastToRoom(String roomId, Message message) {
        if (roomId == null || message == null) return;
        for (ClientSession session : activeSessions.values()) {
            if (roomId.equals(session.getCurrentRoomId()) && session.isConnected()) {
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
        if (session.getPlayerId() != null) {
            roomService.leaveRoom(session.getCurrentRoomId(), session.getPlayerId());
        }
        log.info("Session disconnected: id={}", session.getSessionId());
    }

    public QuizRoomService getRoomService() {
        return roomService;
    }

    public DefaultQuizGameService getGameService() {
        return gameService;
    }
}
