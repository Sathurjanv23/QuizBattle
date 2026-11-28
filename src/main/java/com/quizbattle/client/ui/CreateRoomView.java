package com.quizbattle.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Screen 5: Create Room Confirmation View.
 * Displays the authoritative server-generated room code in an ultra-prominent monospace card,
 * with one-click clipboard copy and lobby entry.
 */
public class CreateRoomView extends VBox {

    private final String roomCode;
    private final String roomName;
    private final String hostUsername;

    private final Label copyFeedbackLabel;

    @SuppressWarnings("this-escape")
    public CreateRoomView(
            String roomCode,
            String roomName,
            String hostUsername,
            Runnable onEnterLobbyRequested,
            Runnable onBackToDashboardRequested) {
        super(20);
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.hostUsername = hostUsername;

        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(540);
        setPadding(new Insets(32, 40, 36, 40));

        // 1. Heading & Host Badge
        Label successIcon = new Label("🎉");
        successIcon.setStyle("-fx-font-size: 36px;");

        Label heading = new Label("Battle Room Created!");
        heading.getStyleClass().add("card-heading");

        Label hostBadge = new Label("👑 You are the Host (" + hostUsername + ")");
        hostBadge.getStyleClass().add("host-crown-badge");

        Label roomNameLabel = new Label("Room Name: " + (roomName != null ? roomName : "Multiplayer Arena"));
        roomNameLabel.getStyleClass().add("card-subtitle");

        VBox headerBox = new VBox(6, successIcon, heading, hostBadge, roomNameLabel);
        headerBox.setAlignment(Pos.CENTER);

        // 2. Prominent Room Code Section
        VBox codeContainer = new VBox(10);
        codeContainer.setAlignment(Pos.CENTER);
        codeContainer.setPadding(new Insets(12, 0, 12, 0));

        Label promptLabel = new Label("SHARE THIS ROOM CODE WITH YOUR FRIENDS");
        promptLabel.getStyleClass().add("field-label");

        Label codeDisplay = new Label(roomCode);
        codeDisplay.getStyleClass().add("room-code-display");

        copyFeedbackLabel = new Label();
        copyFeedbackLabel.getStyleClass().add("status-label");
        copyFeedbackLabel.setStyle("-fx-text-fill: #34D399; -fx-font-weight: bold;");
        copyFeedbackLabel.setVisible(false);

        Button copyButton = new Button("📋 Copy Room Code");
        copyButton.getStyleClass().add("secondary-button");
        copyButton.setOnAction(e -> {
            try {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(roomCode);
                clipboard.setContent(content);
                copyFeedbackLabel.setText("✓ Room code " + roomCode + " copied to clipboard!");
                copyFeedbackLabel.setVisible(true);
            } catch (Throwable ex) {
                copyFeedbackLabel.setText("Code: " + roomCode);
                copyFeedbackLabel.setVisible(true);
            }
        });

        HBox copyRow = new HBox(12, codeDisplay, copyButton);
        copyRow.setAlignment(Pos.CENTER);

        codeContainer.getChildren().addAll(promptLabel, copyRow, copyFeedbackLabel);

        // 3. Navigation Actions
        Button enterLobbyButton = new Button("Enter Multiplayer Lobby ➔");
        enterLobbyButton.getStyleClass().add("primary-button");
        enterLobbyButton.setMaxWidth(Double.MAX_VALUE);
        enterLobbyButton.setStyle("-fx-font-size: 15px; -fx-padding: 13px 24px;");
        enterLobbyButton.setOnAction(e -> {
            if (onEnterLobbyRequested != null) {
                onEnterLobbyRequested.run();
            }
        });

        Button backButton = new Button("⬅ Back to Dashboard");
        backButton.getStyleClass().add("secondary-button");
        backButton.setMaxWidth(Double.MAX_VALUE);
        backButton.setOnAction(e -> {
            if (onBackToDashboardRequested != null) {
                onBackToDashboardRequested.run();
            }
        });

        VBox actions = new VBox(10, enterLobbyButton, backButton);
        actions.setAlignment(Pos.CENTER);

        getChildren().addAll(headerBox, codeContainer, actions);
    }
}
