package com.quizbattle.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizbattle.model.LeaderboardEntry;
import com.quizbattle.model.Player;
import com.quizbattle.model.Question;
import com.quizbattle.model.Room;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.server.concurrent.ConcurrentRoomManager;
import com.quizbattle.server.game.DefaultQuizGameService;
import com.quizbattle.server.game.DefaultQuizGameService.Advance;
import com.quizbattle.server.game.QuizRoomService;
import com.quizbattle.server.sync.ScoreManager;
import com.quizbattle.server.sync.ScoreManager.AnswerOutcome;
import com.quizbattle.server.sync.ScoreManager.LeaderboardSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Transport-neutral message dispatcher orchestrating incoming client requests,
 * server-authoritative sessions, multiplayer room lobbies, and question delivery.
 */
public class DefaultServerMessageDispatcher implements ServerMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerMessageDispatcher.class);
    private static final long ANSWER_FEEDBACK_DELAY_MILLIS = 2_000;

    private final QuizRoomService roomService;
    private final DefaultQuizGameService gameService;
    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<MessageType, BiConsumer<ClientSession, Message>> customHandlers = new ConcurrentHashMap<>();
    private final Map<String, ActiveQuestionTimer> questionTimers = new ConcurrentHashMap<>();
    private final Map<String, Object> roomLifecycleLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService questionScheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "quizbattle-question-deadline");
        thread.setDaemon(true);
        return thread;
    });

    private static final class ActiveQuestionTimer {
        final String questionId;
        final Question question;
        final long deadlineMillis;
        volatile ScheduledFuture<?> future;

        ActiveQuestionTimer(String questionId, Question question, long deadlineMillis) {
            this.questionId = questionId;
            this.question = question;
            this.deadlineMillis = deadlineMillis;
        }

        void cancel() {
            ScheduledFuture<?> scheduled = future;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }
    }

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
            case NEXT_QUESTION -> handleNextQuestion(session, message);
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

        String previousRoomId = session.getCurrentRoomId();
        Room room = roomService.createRoom(session.getPlayerId(), session.getUsername(), roomName, maxPlayers);
        session.setCurrentRoomId(room.getRoomId());
        handlePreviousRoomDeparture(session, previousRoomId, room.getRoomId());

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

        String previousRoomId = session.getCurrentRoomId();
        Room room = roomService.joinRoom(targetRoomId, session.getPlayerId(), session.getUsername());
        if (room == null) {
            session.send(Message.error(message.getRequestId(), "JOIN_FAILED", "Failed to join room '" + targetRoomId + "'"));
            return;
        }

        session.setCurrentRoomId(room.getRoomId());
        handlePreviousRoomDeparture(session, previousRoomId, room.getRoomId());

        // Confirm join to client
        Map<String, Object> respPayload = Map.of(
                "roomId", room.getRoomId(),
                "roomCode", room.getRoomId(),
                "roomName", room.getRoomName()
        );
        session.send(Message.request(MessageType.ROOM_JOINED, message.getRequestId(), respPayload));

        // Broadcast updated room state to all room participants
        broadcastRoomState(room.getRoomId());

        boolean startedDefaultQuiz = false;
        if (ConcurrentRoomManager.DEFAULT_ROOM_ID.equalsIgnoreCase(room.getRoomId())) {
            synchronized (roomLifecycleLock(room.getRoomId())) {
                if (!gameService.isQuizActive(room.getRoomId())) {
                    Map<String, String> participants = new LinkedHashMap<>();
                    room.getPlayers().forEach(p -> participants.put(p.getPlayerId(), p.getUsername()));
                    Optional<Question> firstQuestion = gameService.startQuiz(
                            room.getRoomId(), DefaultQuizGameService.DEFAULT_CATEGORY, participants);
                    firstQuestion.ifPresent(question -> scheduleAndBroadcastQuestion(room.getRoomId(), question));
                    startedDefaultQuiz = true;
                }
            }
        }

        // Mid-quiz joiners become scored participants and receive the existing synchronized deadline.
        if (gameService.isQuizActive(room.getRoomId())) {
            gameService.registerPlayer(room.getRoomId(), session.getPlayerId(), session.getUsername());
            if (!startedDefaultQuiz) {
                sendCurrentQuestion(session, room.getRoomId());
            }
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
        gameService.markDisconnected(roomId, session.getPlayerId());
        session.setCurrentRoomId(null);
        cleanupTerminatedRoom(roomId);

        session.send(Message.request(MessageType.ROOM_LEFT, message.getRequestId(), Map.of(
                "roomId", roomId,
                "roomCode", roomId
        )));

        // Broadcast updated room state to remaining participants
        broadcastRoomState(roomId);
        advanceIfAllConnectedPlayersAnswered(roomId);
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

        synchronized (roomLifecycleLock(roomId)) {
            handleAnswerInRoom(session, message, roomId);
        }
    }

    private void handleAnswerInRoom(ClientSession session, Message message, String roomId) {
        JsonNode payload = message.getPayload();
        if (payload == null || !payload.has("questionId") || !payload.has("selectedOptionIndex")) {
            session.send(Message.error(message.getRequestId(), "BAD_PAYLOAD", "Missing questionId or selectedOptionIndex"));
            return;
        }

        String questionId = payload.get("questionId").asText();
        int selectedOptionIndex = payload.get("selectedOptionIndex").asInt();

        // Room and player always come from the server-side session. If the client names a
        // different room, reject instead of scoring it anywhere.
        AnswerOutcome outcome;
        String claimedRoom = payload.hasNonNull("roomId") ? payload.get("roomId").asText() : null;
        if (claimedRoom != null && !claimedRoom.isBlank() && !claimedRoom.equalsIgnoreCase(roomId)) {
            outcome = new AnswerOutcome(questionId, false, false, 0,
                    gameService.getPlayerScore(roomId, session.getPlayerId()), ScoreManager.RejectReason.WRONG_ROOM);
        } else {
            ActiveQuestionTimer timer = questionTimers.get(roomKey(roomId));
            long deadlineMillis = timer != null && timer.questionId.equals(questionId)
                    ? timer.deadlineMillis
                    : 0;
            if (deadlineMillis > 0 && System.currentTimeMillis() >= deadlineMillis) {
                closeQuestionAndBroadcast(roomId, questionId, timer);
            }
            outcome = gameService.submitAnswerDetailed(
                    roomId, session.getPlayerId(), questionId, selectedOptionIndex, deadlineMillis);
        }

        Map<String, Object> resultPayload = new LinkedHashMap<>();
        resultPayload.put("questionId", questionId);
        resultPayload.put("accepted", outcome.accepted());
        if (!outcome.accepted()) {
            resultPayload.put("reason", outcome.rejectReason().name());
        }
        resultPayload.put("correct", outcome.correct());
        resultPayload.put("pointsAwarded", outcome.pointsAwarded());
        resultPayload.put("currentTotalScore", outcome.totalScore());
        session.send(Message.request(MessageType.ANSWER_RESULT, message.getRequestId(), resultPayload));
        if (outcome.accepted()) {
            advanceIfAllConnectedPlayersAnswered(roomId);
        }
    }

    private void handleStartQuiz(ClientSession session, Message message) {
        String roomId = session.getCurrentRoomId();
        if (roomId == null) {
            session.send(Message.error(message.getRequestId(), "NOT_IN_ROOM", "Cannot advance question outside a room"));
            return;
        }

        if (!isHostOrOpenRoom(session, roomId)) {
            session.send(Message.error(message.getRequestId(), "FORBIDDEN", "Only the room host can start the quiz"));
            return;
        }

        JsonNode payload = message.getPayload();
        String category = payload != null && payload.has("category")
                ? payload.get("category").asText(DefaultQuizGameService.DEFAULT_CATEGORY)
                : DefaultQuizGameService.DEFAULT_CATEGORY;

        // Everyone currently in the room becomes a scored participant of a fresh quiz
        Map<String, String> participants = new LinkedHashMap<>();
        roomService.findRoomById(roomId).ifPresent(room ->
                room.getPlayers().forEach(p -> participants.put(p.getPlayerId(), p.getUsername())));

        synchronized (roomLifecycleLock(roomId)) {
            cancelQuestionTimer(roomId);
            Optional<Question> firstQ = gameService.startQuiz(roomId, category, participants);
            firstQ.ifPresent(q -> scheduleAndBroadcastQuestion(roomId, q));
        }
    }

    /**
     * Legacy protocol command retained for older clients. Quiz progression is server-controlled;
     * hosts cannot close or advance a question manually.
     */
    private void handleNextQuestion(ClientSession session, Message message) {
        String roomId = session.getCurrentRoomId();
        if (roomId == null) {
            session.send(Message.error(message.getRequestId(), "NOT_IN_ROOM", "Cannot advance question outside a room"));
            return;
        }
        if (!isHostOrOpenRoom(session, roomId)) {
            session.send(Message.error(message.getRequestId(), "FORBIDDEN", "Only the room host can advance the quiz"));
            return;
        }
        session.send(Message.error(message.getRequestId(), "AUTOMATIC_PROGRESS",
                "Questions close and advance automatically when all players answer or the timer expires"));
    }

    /**
     * Closes the room's current question if it is {@code questionId}, then broadcasts QUESTION_CLOSED
     * and LEADERBOARD. Quiz progression occurs after a short feedback pause. Safe to call from any
     * thread (including the server deadline scheduler); only the caller that actually closes the
     * question broadcasts.
     *
     * @return true if this call closed the question
     */
    public boolean closeQuestionAndBroadcast(String roomId, String questionId) {
        ActiveQuestionTimer timer = questionTimers.get(roomKey(roomId));
        return timer != null && timer.questionId.equals(questionId)
                && closeQuestionAndBroadcast(roomId, questionId, timer);
    }

    private boolean closeQuestionAndBroadcast(String roomId, String questionId, ActiveQuestionTimer expectedTimer) {
        synchronized (roomLifecycleLock(roomId)) {
            if (questionTimers.get(roomKey(roomId)) != expectedTimer
                    || !expectedTimer.questionId.equals(questionId)) {
                return false;
            }
            Optional<Question> closed = gameService.closeCurrentQuestion(roomId, questionId);
            if (closed.isEmpty()) {
                return false;
            }
            closeQuestionAndScheduleAdvance(roomId, closed.get(), expectedTimer);
            return true;
        }
    }

    private boolean closeQuestionIfAllAnsweredAndBroadcast(String roomId, String questionId) {
        ActiveQuestionTimer timer = questionTimers.get(roomKey(roomId));
        if (timer == null || !timer.questionId.equals(questionId)) {
            return false;
        }
        synchronized (roomLifecycleLock(roomId)) {
            if (questionTimers.get(roomKey(roomId)) != timer) {
                return false;
            }
            Optional<Question> closed = gameService.closeCurrentQuestionIfAllAnswered(roomId, questionId);
            if (closed.isEmpty()) {
                return false;
            }
            closeQuestionAndScheduleAdvance(roomId, closed.get(), timer);
            return true;
        }
    }

    private void closeQuestionAndScheduleAdvance(String roomId, Question closed, ActiveQuestionTimer timer) {
        questionTimers.remove(roomKey(roomId), timer);
        timer.cancel();

        int number = gameService.getQuestionNumber(roomId);
        int total = gameService.getTotalQuestions(roomId);
        boolean last = number >= total;

        Map<String, Object> closedPayload = new LinkedHashMap<>();
        closedPayload.put("roomId", roomId);
        closedPayload.put("questionId", closed.getQuestionId());
        closedPayload.put("correctOptionIndex", closed.getCorrectOptionIndex());
        closedPayload.put("questionNumber", number);
        closedPayload.put("totalQuestions", total);
        closedPayload.put("lastQuestion", last);
        broadcastToRoom(roomId, Message.event(MessageType.QUESTION_CLOSED, closedPayload));
        broadcastLeaderboard(roomId, last);

        questionScheduler.schedule(
                () -> advanceAfterFeedback(roomId, closed.getQuestionId()),
                ANSWER_FEEDBACK_DELAY_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    private void advanceAfterFeedback(String roomId, String closedQuestionId) {
        synchronized (roomLifecycleLock(roomId)) {
            Advance advance = gameService.advanceAfter(roomId, closedQuestionId);
            if (advance.nextQuestion() != null) {
                scheduleAndBroadcastQuestion(roomId, advance.nextQuestion());
            } else if (advance.finished()) {
                broadcastQuizFinished(roomId);
            } else {
                log.warn("Question {} in room {} closed without an advance", closedQuestionId, roomId);
            }
        }
    }

    private void advanceIfAllConnectedPlayersAnswered(String roomId) {
        gameService.getCurrentQuestion(roomId)
                .ifPresent(question -> closeQuestionIfAllAnsweredAndBroadcast(roomId, question.getQuestionId()));
    }

    private void scheduleAndBroadcastQuestion(String roomId, Question question) {
        long startTimeMillis = System.currentTimeMillis();
        long deadlineMillis = startTimeMillis + question.getTimeLimitSeconds() * 1000L;
        int questionNumber = gameService.getQuestionNumber(roomId);
        int totalQuestions = gameService.getTotalQuestions(roomId);
        Question timedQuestion = question.forClient(questionNumber, totalQuestions, startTimeMillis, deadlineMillis);
        ActiveQuestionTimer timer = new ActiveQuestionTimer(
                question.getQuestionId(), timedQuestion, deadlineMillis);
        ActiveQuestionTimer previous = questionTimers.put(roomKey(roomId), timer);
        if (previous != null) {
            previous.cancel();
        }

        long delayMillis = Math.max(0, deadlineMillis - System.currentTimeMillis());
        timer.future = questionScheduler.schedule(
                () -> closeQuestionAndBroadcast(roomId, question.getQuestionId(), timer),
                delayMillis,
                TimeUnit.MILLISECONDS);
        broadcastToRoom(roomId, Message.event(MessageType.QUESTION, timedQuestion));
    }

    private void sendCurrentQuestion(ClientSession session, String roomId) {
        gameService.getCurrentQuestion(roomId).ifPresent(question -> {
            ActiveQuestionTimer timer = questionTimers.get(roomKey(roomId));
            if (timer != null
                    && timer.questionId.equals(question.getQuestionId())
                    && question.getQuestionId().equals(gameService.getScoreManager().getOpenQuestionId(roomId))) {
                session.send(Message.event(MessageType.QUESTION, timer.question));
            }
        });
    }

    private void cancelQuestionTimer(String roomId) {
        ActiveQuestionTimer timer = questionTimers.remove(roomKey(roomId));
        if (timer != null) {
            timer.cancel();
        }
    }

    private void cleanupTerminatedRoom(String roomId) {
        boolean empty = roomService.findRoomById(roomId)
                .map(room -> room.getPlayers().isEmpty())
                .orElse(true);
        if (empty) {
            synchronized (roomLifecycleLock(roomId)) {
                cancelQuestionTimer(roomId);
                gameService.removeRoom(roomId);
            }
        }
    }

    private void handlePreviousRoomDeparture(ClientSession session, String previousRoomId, String currentRoomId) {
        if (previousRoomId == null || previousRoomId.equalsIgnoreCase(currentRoomId)) {
            return;
        }
        gameService.markDisconnected(previousRoomId, session.getPlayerId());
        cleanupTerminatedRoom(previousRoomId);
        broadcastRoomState(previousRoomId);
        advanceIfAllConnectedPlayersAnswered(previousRoomId);
    }

    private Object roomLifecycleLock(String roomId) {
        return roomLifecycleLocks.computeIfAbsent(roomKey(roomId), ignored -> new Object());
    }

    private static String roomKey(String roomId) {
        return roomId == null ? "" : roomId.toUpperCase(Locale.ROOT);
    }

    /** Broadcasts a consistent LEADERBOARD snapshot to everyone in the room. */
    public void broadcastLeaderboard(String roomId, boolean lastQuestion) {
        LeaderboardSnapshot snap = gameService.getLeaderboardSnapshot(roomId);
        Map<String, Object> lb = new LinkedHashMap<>();
        lb.put("roomId", roomId);
        lb.put("questionsClosed", snap.questionsClosed());
        lb.put("totalQuestions", snap.totalQuestions());
        lb.put("lastQuestion", lastQuestion);
        lb.put("entries", snap.entries());
        broadcastToRoom(roomId, Message.event(MessageType.LEADERBOARD, lb));
    }

    private void broadcastQuizFinished(String roomId) {
        LeaderboardSnapshot snap = gameService.getLeaderboardSnapshot(roomId);
        List<String> winners = snap.winners().stream().map(LeaderboardEntry::getUsername).toList();
        Map<String, Object> fin = new LinkedHashMap<>();
        fin.put("roomId", roomId);
        fin.put("totalQuestions", snap.totalQuestions());
        fin.put("winners", winners);
        fin.put("finalStandings", snap.entries());
        fin.put("finishedAt", System.currentTimeMillis());
        broadcastToRoom(roomId, Message.event(MessageType.QUIZ_FINISHED, fin));
        log.info("Quiz finished in room {}. Winners: {}", roomId, winners);
    }

    /** Room host, or any player in a server-owned room such as the quick-play default room. */
    private boolean isHostOrOpenRoom(ClientSession session, String roomId) {
        Optional<Room> roomOpt = roomService.findRoomById(roomId);
        if (roomOpt.isEmpty()) {
            return true;
        }
        Room room = roomOpt.get();
        return session.getPlayerId().equals(room.getHostPlayerId()) || "server-admin".equals(room.getHostPlayerId());
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
            // Keep the score but show the player as disconnected on the leaderboard
            gameService.markDisconnected(roomId, session.getPlayerId());
            cleanupTerminatedRoom(roomId);
            broadcastRoomState(roomId);
            advanceIfAllConnectedPlayersAnswered(roomId);
        }
        log.info("Session disconnected: id={}", session.getSessionId());
    }

    @Override
    public void close() {
        questionTimers.values().forEach(ActiveQuestionTimer::cancel);
        questionTimers.clear();
        questionScheduler.shutdownNow();
        roomLifecycleLocks.clear();
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
