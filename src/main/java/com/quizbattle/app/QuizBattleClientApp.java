package com.quizbattle.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizbattle.client.net.ClientConnectionState;
import com.quizbattle.client.net.NetworkClient;
import com.quizbattle.client.net.SocketNetworkClient;
import com.quizbattle.client.ui.CreateRoomView;
import com.quizbattle.client.ui.HostDashboardView;
import com.quizbattle.client.ui.JoinRoomView;
import com.quizbattle.client.ui.ManualConnectView;
import com.quizbattle.client.ui.MultiplayerLobbyView;
import com.quizbattle.client.ui.NavigationContainer;
import com.quizbattle.client.ui.PlayerHomeView;
import com.quizbattle.client.ui.UiCallbackDispatcher;
import com.quizbattle.client.ui.WelcomeView;
import com.quizbattle.model.Question;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * JavaFX Client Application orchestrating Welcome, Manual Connect,
 * Host Dashboard, Create Room, Join Room, Multiplayer Lobby, and Quiz views.
 */
public class QuizBattleClientApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(QuizBattleClientApp.class);

    private NavigationContainer rootContainer;
    private NetworkClient networkClient;

    private String currentUsername;
    private String currentPlayerId;
    private String currentHostPort;
    private String currentRoomCode;
    private String currentRoomName;

    private ManualConnectView manualConnectView;
    private HostDashboardView hostDashboardView;
    private CreateRoomView createRoomView;
    private JoinRoomView joinRoomView;
    private MultiplayerLobbyView multiplayerLobbyView;
    private PlayerHomeView playerHomeView;

    @Override
    public void start(Stage primaryStage) {
        log.info("Starting QuizBattle 2.0 JavaFX Client Application");

        rootContainer = new NavigationContainer();
        networkClient = new SocketNetworkClient();

        setupNetworkListeners();
        showWelcomeScreen();

        Scene scene = new Scene(rootContainer, 880, 640);
        URL cssResource = getClass().getResource("/styles/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        primaryStage.setTitle("QuizBattle 2.0 - Client");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(680);
        primaryStage.setMinHeight(520);

        primaryStage.setOnCloseRequest(e -> {
            log.info("Client window closing, releasing network resources...");
            if (networkClient != null) {
                networkClient.disconnect();
            }
        });

        primaryStage.show();
    }

    private void showWelcomeScreen() {
        WelcomeView welcomeView = new WelcomeView(username -> {
            this.currentUsername = username;
            showManualConnectScreen();
        });
        rootContainer.navigateTo(welcomeView);
        rootContainer.setConnectionStatus("Offline", "#94a3b8");
    }

    private void showManualConnectScreen() {
        manualConnectView = new ManualConnectView(
                currentUsername,
                this::initiateConnection,
                this::showWelcomeScreen
        );
        rootContainer.navigateTo(manualConnectView);
        rootContainer.setConnectionStatus("Disconnected", "#f59e0b");
    }

    private void initiateConnection(String host, int port) {
        this.currentHostPort = host + ":" + port;
        rootContainer.setConnectionStatus("Connecting...", "#3b82f6");

        // Execute socket connection asynchronously to NEVER freeze the JavaFX thread
        ForkJoinPool.commonPool().execute(() -> {
            try {
                networkClient.connect(host, port);
                log.info("Socket connected. Sending handshake CONNECT for username '{}'...", currentUsername);

                // Send CONNECT request
                String reqId = UUID.randomUUID().toString();
                networkClient.send(Message.request(MessageType.CONNECT, reqId, Map.of("username", currentUsername)));
            } catch (Exception e) {
                log.error("Failed to connect to {}:{}: {}", host, port, e.getMessage());
                UiCallbackDispatcher.runOnFxThread(() -> {
                    rootContainer.setConnectionStatus("Connection Failed", "#ef4444");
                    if (manualConnectView != null) {
                        manualConnectView.showError(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                });
            }
        });
    }

    private void setupNetworkListeners() {
        // 1. Connection State Transitions
        networkClient.onConnectionStateChange(state -> {
            log.info("Network state change observed: {}", state);
            if (state == ClientConnectionState.DISCONNECTED || state == ClientConnectionState.ERROR) {
                rootContainer.setConnectionStatus("Disconnected", "#ef4444");
            } else if (state == ClientConnectionState.CONNECTED) {
                rootContainer.setConnectionStatus("Connected", "#10b981");
            }
        });

        // 2. CONNECTED event
        networkClient.registerListener(MessageType.CONNECTED, message -> {
            JsonNode payload = message.getPayload();
            if (payload != null && payload.has("playerId")) {
                this.currentPlayerId = payload.get("playerId").asText();
            } else {
                this.currentPlayerId = "usr-" + UUID.randomUUID().toString().substring(0, 6);
            }
            log.info("Handshake acknowledged by server. PlayerId={}", currentPlayerId);
            showHostDashboardScreen();
        });

        // 3. ROOM_CREATED event (Screen 2: Create Room Confirmation)
        networkClient.registerListener(MessageType.ROOM_CREATED, message -> {
            JsonNode payload = message.getPayload();
            this.currentRoomCode = payload != null && payload.has("roomCode")
                    ? payload.get("roomCode").asText()
                    : (payload != null && payload.has("roomId") ? payload.get("roomId").asText() : "QB0000");
            this.currentRoomName = payload != null && payload.has("roomName")
                    ? payload.get("roomName").asText()
                    : "Multiplayer Arena";

            log.info("Room created by server: code={}, name={}", currentRoomCode, currentRoomName);
            showCreateRoomConfirmationScreen();
        });

        // 4. ROOM_JOINED event (Screen 4: Enter Lobby or Default Room)
        networkClient.registerListener(MessageType.ROOM_JOINED, message -> {
            JsonNode payload = message.getPayload();
            String roomId = payload != null && payload.has("roomCode")
                    ? payload.get("roomCode").asText()
                    : (payload != null && payload.has("roomId") ? payload.get("roomId").asText() : "room-default");
            String roomName = payload != null && payload.has("roomName") ? payload.get("roomName").asText() : "Trivia Arena";

            this.currentRoomCode = roomId;
            this.currentRoomName = roomName;
            log.info("Room joined: {} ({})", roomName, roomId);

            if ("room-default".equalsIgnoreCase(roomId)) {
                // Member 1 Quick Play mode
                if (playerHomeView != null) {
                    playerHomeView.onRoomJoined(roomId, roomName);
                }
            } else {
                // Member 2 Multiplayer Lobby mode
                showMultiplayerLobbyScreen();
            }
        });

        // 5. ROOM_STATE broadcast event (Live Lobby updates)
        networkClient.registerListener(MessageType.ROOM_STATE, message -> {
            JsonNode payload = message.getPayload();
            log.debug("Received ROOM_STATE broadcast: {}", payload);
            if (multiplayerLobbyView != null && payload != null) {
                multiplayerLobbyView.updateRoomState(payload);
            }
        });

        // 6. ROOM_LEFT event
        networkClient.registerListener(MessageType.ROOM_LEFT, message -> {
            log.info("Left room, returning to dashboard.");
            currentRoomCode = null;
            multiplayerLobbyView = null;
            showHostDashboardScreen();
        });

        // 7. QUESTION event (Transitions to trivia question display)
        networkClient.registerListener(MessageType.QUESTION, message -> {
            try {
                Question question = message.getPayloadAs(Question.class);
                if (question != null) {
                    log.info("Received active question: id='{}', text='{}'", question.getQuestionId(), question.getText());
                    showQuestionInPlayerHome(question);
                }
            } catch (Exception e) {
                log.error("Failed to parse QUESTION payload: {}", e.getMessage(), e);
            }
        });

        // 8. ANSWER_RESULT event
        networkClient.registerListener(MessageType.ANSWER_RESULT, message -> {
            JsonNode payload = message.getPayload();
            if (payload != null && playerHomeView != null) {
                boolean correct = payload.path("correct").asBoolean(false);
                int points = payload.path("pointsAwarded").asInt(0);
                int totalScore = payload.path("currentTotalScore").asInt(0);
                log.info("Answer result: correct={}, points={}, totalScore={}", correct, points, totalScore);
                playerHomeView.displayAnswerResult(correct, points, totalScore);
            }
        });

        // 9. ERROR event
        networkClient.registerListener(MessageType.ERROR, message -> {
            JsonNode payload = message.getPayload();
            String code = payload != null && payload.has("code") ? payload.get("code").asText() : "ERROR";
            String msg = payload != null && payload.has("message") ? payload.get("message").asText() : "Unknown error";
            log.error("Received server error: code={}, message={}", code, msg);

            UiCallbackDispatcher.runOnFxThread(() -> {
                if (joinRoomView != null) {
                    joinRoomView.showError(code + ": " + msg);
                }
                if (hostDashboardView != null) {
                    hostDashboardView.showError(code + ": " + msg);
                }
                if (manualConnectView != null) {
                    manualConnectView.showError(code + ": " + msg);
                }
            });
        });
    }

    // ==========================================
    // Screen Navigation Methods
    // ==========================================

    /**
     * SCREEN 1 — HOST DASHBOARD
     */
    private void showHostDashboardScreen() {
        hostDashboardView = new HostDashboardView(
                currentUsername,
                currentPlayerId,
                currentHostPort,
                this::requestCreateRoom,
                this::showJoinRoomScreen,
                this::startQuickPlay,
                this::disconnectAndReturn
        );
        rootContainer.navigateTo(hostDashboardView);
    }

    /**
     * SCREEN 2 — CREATE ROOM CONFIRMATION
     */
    private void showCreateRoomConfirmationScreen() {
        createRoomView = new CreateRoomView(
                currentRoomCode,
                currentRoomName,
                currentUsername,
                this::showMultiplayerLobbyScreen,
                this::showHostDashboardScreen
        );
        rootContainer.navigateTo(createRoomView);
    }

    /**
     * SCREEN 3 — JOIN ROOM
     */
    private void showJoinRoomScreen() {
        joinRoomView = new JoinRoomView(
                currentUsername,
                this::requestJoinRoom,
                this::showHostDashboardScreen
        );
        rootContainer.navigateTo(joinRoomView);
    }

    /**
     * SCREEN 4 — MULTIPLAYER LOBBY
     */
    private void showMultiplayerLobbyScreen() {
        multiplayerLobbyView = new MultiplayerLobbyView(
                currentRoomCode,
                currentRoomName,
                currentPlayerId,
                currentUsername,
                currentHostPort,
                this::startQuizInRoom,
                this::leaveCurrentRoom
        );
        rootContainer.navigateTo(multiplayerLobbyView);
    }

    /**
     * Preserves Member 1's Player Home & Trivia Question View.
     */
    private void showQuestionInPlayerHome(Question question) {
        if (playerHomeView == null) {
            playerHomeView = new PlayerHomeView(
                    currentUsername,
                    currentPlayerId,
                    currentHostPort,
                    this::joinDefaultRoom,
                    this::submitAnswer,
                    this::requestNextQuestion,
                    this::disconnectAndReturn
            );
        }
        playerHomeView.onRoomJoined(currentRoomCode != null ? currentRoomCode : "room-default",
                currentRoomName != null ? currentRoomName : "General Trivia Arena");
        playerHomeView.displayQuestion(question, this::submitAnswer);
        rootContainer.navigateTo(playerHomeView);
    }

    private void startQuickPlay() {
        showPlayerHomeScreen();
        joinDefaultRoom();
    }

    private void showPlayerHomeScreen() {
        playerHomeView = new PlayerHomeView(
                currentUsername,
                currentPlayerId,
                currentHostPort,
                this::joinDefaultRoom,
                this::submitAnswer,
                this::requestNextQuestion,
                this::disconnectAndReturn
        );
        rootContainer.navigateTo(playerHomeView);
    }

    // ==========================================
    // Network Outbound Requests
    // ==========================================

    private void requestCreateRoom() {
        log.info("Transmitting CREATE_ROOM request to server...");
        String reqId = UUID.randomUUID().toString();
        networkClient.send(Message.request(MessageType.CREATE_ROOM, reqId, Map.of(
                "roomName", currentUsername + "'s Arena",
                "maxPlayers", 8
        )));
    }

    private void requestJoinRoom(String roomCode) {
        log.info("Transmitting JOIN_ROOM request for room code '{}'...", roomCode);
        String reqId = UUID.randomUUID().toString();
        networkClient.send(Message.request(MessageType.JOIN_ROOM, reqId, Map.of(
                "roomCode", roomCode
        )));
    }

    private void startQuizInRoom(String category) {
        log.info("Host requesting START_QUIZ in room '{}'...", currentRoomCode);
        String reqId = UUID.randomUUID().toString();
        networkClient.send(Message.request(MessageType.START_QUIZ, reqId, Map.of(
                "roomId", currentRoomCode != null ? currentRoomCode : "room-default",
                "category", category != null ? category : "General Trivia"
        )));
    }

    private void leaveCurrentRoom() {
        log.info("Requesting to LEAVE_ROOM '{}'...", currentRoomCode);
        String reqId = UUID.randomUUID().toString();
        networkClient.send(Message.request(MessageType.LEAVE_ROOM, reqId, Map.of(
                "roomId", currentRoomCode != null ? currentRoomCode : "room-default"
        )));
    }

    private void joinDefaultRoom() {
        log.info("Requesting to join default room...");
        String reqId = UUID.randomUUID().toString();
        networkClient.send(Message.request(MessageType.JOIN_ROOM, reqId, Map.of("roomId", "room-default")));
    }

    private void submitAnswer(String questionId, int selectedOptionIndex) {
        log.info("Submitting answer for question {}: option {}", questionId, selectedOptionIndex);
        String reqId = UUID.randomUUID().toString();
        networkClient.send(Message.request(MessageType.ANSWER, reqId, Map.of(
                "questionId", questionId,
                "selectedOptionIndex", selectedOptionIndex
        )));
    }

    private void requestNextQuestion() {
        log.info("Requesting next trivia question...");
        String reqId = UUID.randomUUID().toString();
        networkClient.send(Message.request(MessageType.START_QUIZ, reqId, Map.of(
                "roomId", currentRoomCode != null ? currentRoomCode : "room-default"
        )));
    }

    private void disconnectAndReturn() {
        log.info("User requested disconnect. Returning to manual connect screen...");
        if (networkClient != null) {
            networkClient.disconnect();
        }
        showManualConnectScreen();
    }
}
