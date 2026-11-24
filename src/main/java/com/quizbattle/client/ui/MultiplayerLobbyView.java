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

import java.util.function.Consumer;

/**
 * Screen 4: Real-Time Multiplayer Lobby View.
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
    private final Button selectCategoryButton;
    private final Label startQuizHint;
    private final Label copyFeedback;

    @SuppressWarnings("this-escape")
    public MultiplayerLobbyView(
            String roomCode,
            String roomName,
            String currentPlayerId,
            String currentUsername,
            String hostPort,
            Runnable onSelectCategoryRequested,
            Runnable onLeaveRoomRequested) {
        super(16);
        this.roomCode = roomCode;
        this.roomName = roomName != null ? roomName : "Multiplayer Battle Room";
        this.currentPlayerId = currentPlayerId;
        this.currentUsername = currentUsername;
        this.hostPort = hostPort;
        this.onSelectCategoryRequested = onSelectCategoryRequested;
        this.onLeaveRoomRequested = onLeaveRoomRequested;

        getStyleClass().add("player-home-container");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(720);
        setPadding(new Insets(20));

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
        VBox metaCard = new VBox(10);
        metaCard.getStyleClass().add("welcome-card");
        metaCard.setPadding(new Insets(18, 24, 18, 24));
        metaCard.setAlignment(Pos.CENTER);

        HBox codeRow = new HBox(12);
        codeRow.setAlignment(Pos.CENTER);

        roomCodeBadge = new Label("ROOM CODE: " + roomCode);
        roomCodeBadge.getStyleClass().add("room-badge");
        roomCodeBadge.setStyle("-fx-font-size: 16px; -fx-padding: 6px 14px;");

        copyFeedback = new Label();
        copyFeedback.getStyleClass().add("status-label");
        copyFeedback.setVisible(false);

        Button copyCodeBtn = new Button("📋 Copy");
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

        HBox hostAndCountRow = new HBox(20);
        hostAndCountRow.setAlignment(Pos.CENTER);

        hostStatusLabel = new Label("👑 Host: Loading...");
        hostStatusLabel.getStyleClass().add("player-badge");

        playerCountBadge = new Label("Players: 1");
        playerCountBadge.getStyleClass().add("score-badge");

        hostAndCountRow.getChildren().addAll(hostStatusLabel, playerCountBadge);

        metaCard.getChildren().addAll(codeRow, roomNameLabel, hostAndCountRow);

        // 3. Live Player Roster Section
        VBox rosterSection = new VBox(10);
        rosterSection.getStyleClass().add("welcome-card");
        rosterSection.setPadding(new Insets(20));
        rosterSection.setAlignment(Pos.TOP_LEFT);

        Label rosterTitle = new Label("Connected Players in Lobby");
        rosterTitle.getStyleClass().add("field-label");

        playerRosterBox = new VBox(8);
        playerRosterBox.setAlignment(Pos.TOP_CENTER);
        playerRosterBox.setPadding(new Insets(8, 0, 8, 0));

        ScrollPane rosterScroll = new ScrollPane(playerRosterBox);
        rosterScroll.setFitToWidth(true);
        rosterScroll.setPrefHeight(180);
        rosterScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        rosterSection.getChildren().addAll(rosterTitle, rosterScroll);

        // 4. Host Action Section (Category Selection)
        VBox actionSection = new VBox(10);
        actionSection.setAlignment(Pos.CENTER);
        actionSection.setPadding(new Insets(12));

        Label categoryLabel = new Label("Ready to play?");
        categoryLabel.getStyleClass().add("field-label");

        selectCategoryButton = new Button("🎯 Select Quiz Category");
        selectCategoryButton.getStyleClass().add("primary-button");
        selectCategoryButton.setMaxWidth(360);
        selectCategoryButton.setStyle("-fx-font-size: 15px; -fx-padding: 12px 24px;");
        selectCategoryButton.setOnAction(e -> {
            if (onSelectCategoryRequested != null) {
                onSelectCategoryRequested.run();
            }
        });

        startQuizHint = new Label("Waiting for host to start the quiz round...");
        startQuizHint.getStyleClass().add("card-subtitle");

        actionSection.getChildren().addAll(categoryLabel, selectCategoryButton, startQuizHint);

        getChildren().addAll(topBar, metaCard, rosterSection, actionSection);

        // Initial single-player placeholder
        addPlayerRow(currentUsername, currentPlayerId, false);
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
                hostStatusLabel.setText("👑 Host: " + this.hostUsername);
            }

            this.isHost = currentPlayerId != null && currentPlayerId.equals(this.hostPlayerId);

            if (payload.has("players") && payload.get("players").isArray()) {
                JsonNode playersNode = payload.get("players");
                playerRosterBox.getChildren().clear();

                int count = playersNode.size();
                playerCountBadge.setText("Players: " + count);

                for (JsonNode pNode : playersNode) {
                    String pId = pNode.path("playerId").asText();
                    String pName = pNode.path("username").asText(pId);
                    boolean pIsHost = (pNode.has("isHost") && pNode.get("isHost").asBoolean()) ||
                            (hostPlayerId != null && hostPlayerId.equals(pId));
                    addPlayerRow(pName, pId, pIsHost);
                }
            } else if (payload.has("playerCount")) {
                playerCountBadge.setText("Players: " + payload.get("playerCount").asInt());
            }

            // Update Start Quiz button controls
            if (isHost) {
                selectCategoryButton.setDisable(false);
                selectCategoryButton.setText("🎯 Select Quiz Category");
                startQuizHint.setText("You are the host! Choose a category when participants are ready.");
            } else {
                selectCategoryButton.setDisable(true);
                selectCategoryButton.setText("⏳ Waiting for Host...");
                startQuizHint.setText("Only the host (" + (hostUsername != null ? hostUsername : "Host") + ") can start the quiz.");
            }
        });
    }

    private void addPlayerRow(String username, String playerId, boolean isHostPlayer) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 14, 8, 14));
        row.setStyle("-fx-background-color: #0f172a; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-radius: 6px; -fx-border-width: 1px;");

        Label icon = new Label(isHostPlayer ? "👑" : "👤");
        icon.setStyle("-fx-font-size: 16px;");

        Label name = new Label(username + (playerId.equals(currentPlayerId) ? " (You)" : ""));
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: #f1f5f9; -fx-font-size: 13px;");

        Region rowSpacer = new Region();
        HBox.setHgrow(rowSpacer, Priority.ALWAYS);

        Label roleBadge = new Label(isHostPlayer ? "HOST" : "PLAYER");
        roleBadge.setStyle(isHostPlayer
                ? "-fx-background-color: #7c2d12; -fx-text-fill: #fdba74; -fx-padding: 3px 8px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: bold;"
                : "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8; -fx-padding: 3px 8px; -fx-background-radius: 6px; -fx-font-size: 11px;");

        row.getChildren().addAll(icon, name, rowSpacer, roleBadge);
        playerRosterBox.getChildren().add(row);
    }

    public String getRoomCode() {
        return roomCode;
    }

    public boolean isHost() {
        return isHost;
    }
}
