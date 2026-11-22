package com.quizbattle.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizbattle.client.net.ClientConnectionState;
import com.quizbattle.client.net.NetworkClient;
import com.quizbattle.client.net.SocketNetworkClient;
import com.quizbattle.client.ui.ManualConnectView;
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

/**
 * JavaFX Client Application orchestrating Welcome, Manual Connect,
 * and Player Home views with non-blocking TCP network operations.
 */
public class QuizBattleClientApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(QuizBattleClientApp.class);

    private NavigationContainer rootContainer;
    private NetworkClient networkClient;

    private String currentUsername;
    private String currentPlayerId;
    private String currentHostPort;

    private ManualConnectView manualConnectView;
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
            showPlayerHomeScreen();
        });

        // 3. ROOM_JOINED event
        networkClient.registerListener(MessageType.ROOM_JOINED, message -> {
            JsonNode payload = message.getPayload();
            String roomId = payload != null && payload.has("roomId") ? payload.get("roomId").asText() : "room-default";
            String roomName = payload != null && payload.has("roomName") ? payload.get("roomName").asText() : "General Trivia";
            log.info("Room joined: {} ({})", roomName, roomId);
            if (playerHomeView != null) {
                playerHomeView.onRoomJoined(roomId, roomName);
            }
        });

        // 4. QUESTION event
        networkClient.registerListener(MessageType.QUESTION, message -> {
            try {
                Question question = message.getPayloadAs(Question.class);
                if (question != null && playerHomeView != null) {
                    log.info("Received active question: id='{}', text='{}'", question.getQuestionId(), question.getText());
                    playerHomeView.displayQuestion(question, this::submitAnswer);
                }
            } catch (Exception e) {
                log.error("Failed to parse QUESTION payload: {}", e.getMessage(), e);
            }
        });

        // 5. ANSWER_RESULT event
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

        // 6. ERROR event
        networkClient.registerListener(MessageType.ERROR, message -> {
            JsonNode payload = message.getPayload();
            String code = payload != null && payload.has("code") ? payload.get("code").asText() : "ERROR";
            String msg = payload != null && payload.has("message") ? payload.get("message").asText() : "Unknown error";
            log.error("Received server error: code={}, message={}", code, msg);
            if (manualConnectView != null) {
                manualConnectView.showError(code + ": " + msg);
            }
        });
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
        networkClient.send(Message.request(MessageType.START_QUIZ, reqId, Map.of("roomId", "room-default")));
    }

    private void disconnectAndReturn() {
        log.info("User requested disconnect. Returning to manual connect screen...");
        if (networkClient != null) {
            networkClient.disconnect();
        }
        showManualConnectScreen();
    }
}
