package com.quizbattle.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 7: Real-Time Multiplayer Lobby View.
 * Displays live room state, host indicator, synchronized participant roster,
 * dynamic host controls (Start Quiz), and room exit actions.
 */
public class MultiplayerLobbyView extends VBox {

    private final String currentPlayerId;
    private final String currentUsername;
    private final String hostPort;
    private final Runnable onSelectCategoryRequested;
    private final Runnable onLeaveRoomRequested;

    private String roomCode;
    private String roomName;
    private String hostPlayerId;
    private String hostUsername;
    private boolean isHost = false;

    // UI Controls
    private final Label roomCodeBadge;
    private final Label roomNameLabel;
    private final Label hostStatusLabel;
    private final Label playerCountBadge;
    private final VBox playerRosterBox;
    private final Button startQuizButton;
    private final Label startQuizHint;
    private final Label copyFeedback;

    public MultiplayerLobbyView(
            String roomCode,
            String roomName,
            String currentPlayerId,
            String currentUsername,
            String hostPort,
            Runnable onSelectCategoryRequested,
            Runnable onLeaveRoomRequested) {
        this(roomCode, roomName, currentPlayerId, currentUsername, hostPort, null, onSelectCategoryRequested, onLeaveRoomRequested);
    }

    @SuppressWarnings("this-escape")
    public MultiplayerLobbyView(
            String roomCode,
            String roomName,
            String currentPlayerId,
            String currentUsername,
            String hostPort,
            String initialHostPlayerId,
            Runnable onSelectCategoryRequested,
            Runnable onLeaveRoomRequested) {
        super(18);
        this.roomCode = roomCode != null ? roomCode : "QB0000";
        this.roomName = roomName != null ? roomName : "Multiplayer Battle Room";
        this.currentPlayerId = currentPlayerId;
        this.currentUsername = currentUsername;
        this.hostPort = hostPort;
        this.hostPlayerId = initialHostPlayerId;
        this.onSelectCategoryRequested = onSelectCategoryRequested;
        this.onLeaveRoomRequested = onLeaveRoomRequested;

        if (initialHostPlayerId != null && initialHostPlayerId.equals(currentPlayerId)) {
            this.isHost = true;
            this.hostUsername = currentUsername;
        }

        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(720);
        setPadding(new Insets(24, 30, 28, 30));

        // 1. Top Navigation & Status Bar
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("player-header-bar");
        topBar.setPadding(new Insets(10, 16, 10, 16));

        Label playerLabel = new Label("👤 " + currentUsername + " (" + currentPlayerId + ")");
        playerLabel.getStyleClass().add("player-identity-label");

        Label connectionBadge = new Label("🟢 TCP: " + hostPort);
        connectionBadge.getStyleClass().add("connected-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button leaveButton = new Button("Leave Room");
        leaveButton.getStyleClass().add("danger-button");
        leaveButton.setOnAction(e -> {
            if (onLeaveRoomRequested != null) {
                onLeaveRoomRequested.run();
            }
        });

        topBar.getChildren().addAll(playerLabel, connectionBadge, spacer, leaveButton);

        // 2. Room Metadata Card
        VBox metaCard = new VBox(12);
        metaCard.getStyleClass().add("battle-action-card");
        metaCard.setPadding(new Insets(20, 24, 20, 24));
        metaCard.setAlignment(Pos.CENTER);

        HBox codeRow = new HBox(12);
        codeRow.setAlignment(Pos.CENTER);

        roomCodeBadge = new Label("ROOM CODE: " + this.roomCode);
        roomCodeBadge.getStyleClass().add("room-badge");
        roomCodeBadge.setStyle("-fx-font-size: 16px; -fx-padding: 6px 16px;");

        copyFeedback = new Label();
        copyFeedback.getStyleClass().add("status-label");
        copyFeedback.setStyle("-fx-text-fill: #34D399; -fx-font-weight: bold;");
        copyFeedback.setVisible(false);

        Button copyCodeBtn = new Button("📋 Copy Code");
        copyCodeBtn.getStyleClass().add("secondary-button");
        copyCodeBtn.setOnAction(e -> {
            try {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(this.roomCode);
                clipboard.setContent(content);
                copyFeedback.setText("✓ Copied!");
                copyFeedback.setVisible(true);
            } catch (Throwable ignored) {
            }
        });

        codeRow.getChildren().addAll(roomCodeBadge, copyCodeBtn, copyFeedback);

        roomNameLabel = new Label(this.roomName);
        roomNameLabel.getStyleClass().add("card-heading");

        HBox hostAndCountRow = new HBox(16);
        hostAndCountRow.setAlignment(Pos.CENTER);

        String initialHostText = isHost ? "👑 Host: " + currentUsername + " (You)" : (hostUsername != null ? "👑 Host: " + hostUsername : "👑 Host: Loading...");
        hostStatusLabel = new Label(initialHostText);
        hostStatusLabel.getStyleClass().add("host-crown-badge");

        playerCountBadge = new Label("👥 Players: 1");
        playerCountBadge.getStyleClass().add("score-badge");

        hostAndCountRow.getChildren().addAll(hostStatusLabel, playerCountBadge);

        metaCard.getChildren().addAll(codeRow, roomNameLabel, hostAndCountRow);

        // 3. Live Player Roster Section
        VBox rosterSection = new VBox(10);
        rosterSection.getStyleClass().add("battle-action-card");
        rosterSection.setPadding(new Insets(16, 20, 16, 20));
        rosterSection.setAlignment(Pos.TOP_LEFT);

        Label rosterTitle = new Label("CONNECTED PLAYERS IN LOBBY");
        rosterTitle.getStyleClass().add("field-label");

        playerRosterBox = new VBox(8);
        playerRosterBox.setAlignment(Pos.TOP_CENTER);
        playerRosterBox.setPadding(new Insets(4, 0, 4, 0));

        ScrollPane rosterScroll = new ScrollPane(playerRosterBox);
        rosterScroll.setFitToWidth(true);
        rosterScroll.setPrefHeight(180);
        rosterScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        rosterSection.getChildren().addAll(rosterTitle, rosterScroll);

        // 4. Host Action Section
        VBox actionSection = new VBox(10);
        actionSection.setAlignment(Pos.CENTER);
        actionSection.setPadding(new Insets(8));

        startQuizButton = new Button("Choose Quiz Category ➔");
        startQuizButton.getStyleClass().add("primary-button");
        startQuizButton.setMaxWidth(380);
        startQuizButton.setStyle("-fx-font-size: 15px; -fx-padding: 13px 28px;");
        startQuizButton.setDisable(!isHost);
        startQuizButton.setOnAction(e -> {
            if (onSelectCategoryRequested != null) {
                onSelectCategoryRequested.run();
            }
        });

        String initialHintText = isHost
                ? "You are the host! Choose a quiz category to start the battle when all players are ready."
                : "Waiting for the room host to start the quiz round...";
        startQuizHint = new Label(initialHintText);
        startQuizHint.getStyleClass().add("card-subtitle");

        actionSection.getChildren().addAll(startQuizButton, startQuizHint);

        getChildren().addAll(topBar, metaCard, rosterSection, actionSection);

        // Initial player row
        addPlayerRow(currentUsername, currentPlayerId, isHost);
    }

    /**
     * Updates lobby state from incoming authoritative server ROOM_STATE payload.
     */
    public void updateRoomState(JsonNode payload) {
        if (payload == null) return;

        UiCallbackDispatcher.runOnFxThread(() -> {
            if (payload.has("roomCode")) {
                this.roomCode = payload.get("roomCode").asText();
                roomCodeBadge.setText("ROOM CODE: " + this.roomCode);
            } else if (payload.has("roomId")) {
                this.roomCode = payload.get("roomId").asText();
                roomCodeBadge.setText("ROOM CODE: " + this.roomCode);
            }

            if (payload.has("roomName")) {
                this.roomName = payload.get("roomName").asText();
                roomNameLabel.setText(this.roomName);
            }

            if (payload.has("hostPlayerId")) {
                this.hostPlayerId = payload.get("hostPlayerId").asText();
            }

            if (payload.has("host")) {
                this.hostUsername = payload.get("host").asText();
            }

            this.isHost = currentPlayerId != null && currentPlayerId.equals(this.hostPlayerId);

            if (this.hostUsername != null) {
                hostStatusLabel.setText("👑 Host: " + this.hostUsername + (isHost ? " (You)" : ""));
            } else if (isHost) {
                hostStatusLabel.setText("👑 Host: " + currentUsername + " (You)");
            }

            if (payload.has("players") && payload.get("players").isArray()) {
                JsonNode playersNode = payload.get("players");
                playerRosterBox.getChildren().clear();

                int count = playersNode.size();
                playerCountBadge.setText("👥 Players: " + count);

                for (JsonNode pNode : playersNode) {
                    String pId = pNode.path("playerId").asText();
                    String pName = pNode.path("username").asText(pId);
                    boolean pIsHost = (pNode.has("isHost") && pNode.get("isHost").asBoolean()) ||
                            (hostPlayerId != null && hostPlayerId.equals(pId));
                    addPlayerRow(pName, pId, pIsHost);
                }
            } else if (payload.has("playerCount")) {
                playerCountBadge.setText("👥 Players: " + payload.get("playerCount").asInt());
            }

            // Update Start Quiz button controls
            if (isHost) {
                startQuizButton.setDisable(false);
                startQuizButton.setText("Choose Quiz Category ➔");
                startQuizHint.setText("You are the host! Choose a quiz category to start the battle when all players are ready.");
            } else {
                startQuizButton.setDisable(true);
                startQuizButton.setText("Waiting for Host...");
                startQuizHint.setText("Only the room host (" + (hostUsername != null ? hostUsername : "Host") + ") can start the quiz.");
            }
        });
    }

    private void addPlayerRow(String username, String playerId, boolean isHostPlayer) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 16, 10, 16));
        row.setStyle("-fx-background-color: #141E33; -fx-background-radius: 8px; -fx-border-color: "
                + (isHostPlayer ? "#F59E0B" : "#2D3E5D") + "; -fx-border-radius: 8px; -fx-border-width: 1px;");

        // Avatar Initial
        String initial = (username != null && !username.isBlank()) ? String.valueOf(Character.toUpperCase(username.charAt(0))) : "?";
        Label avatar = new Label(initial);
        avatar.setStyle("-fx-background-color: " + (isHostPlayer ? "#451A03" : "#1E3A5F") + "; -fx-text-fill: "
                + (isHostPlayer ? "#FBBF24" : "#38BDF8") + "; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 4px 9px; -fx-background-radius: 50%;");

        boolean isMe = playerId != null && playerId.equals(currentPlayerId);
        Label name = new Label(username + (isMe ? " (You)" : ""));
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: #F1F5F9; -fx-font-size: 14px;");

        Region rowSpacer = new Region();
        HBox.setHgrow(rowSpacer, Priority.ALWAYS);

        Label connectionIndicator = new Label("🟢 Connected");
        connectionIndicator.setStyle("-fx-font-size: 11px; -fx-text-fill: #34D399; -fx-font-weight: bold;");

        Label roleBadge = new Label(isHostPlayer ? "👑 HOST" : "⚔️ PLAYER");
        roleBadge.getStyleClass().add(isHostPlayer ? "host-crown-badge" : "player-role-badge");

        row.getChildren().addAll(avatar, name, rowSpacer, connectionIndicator, roleBadge);
        playerRosterBox.getChildren().add(row);
    }

    public String getRoomCode() {
        return roomCode;
    }

    public boolean isHost() {
        return isHost;
    }
}
