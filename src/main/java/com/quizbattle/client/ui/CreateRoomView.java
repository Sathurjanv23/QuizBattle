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
 * Screen 2: Create Room Confirmation View.
 * Displays the authoritative server-generated room code, copy-to-clipboard action,
 * host identity confirmation, and lobby navigation.
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
        super(18);
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.hostUsername = hostUsername;

        getStyleClass().add("welcome-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(520);
        setPadding(new Insets(32));

        Label heading = new Label("Room Created Successfully! 🎉");
        heading.getStyleClass().add("card-heading");

        Label hostBadge = new Label("👑 You are the Host (" + hostUsername + ")");
        hostBadge.getStyleClass().add("player-badge");

        Label roomNameLabel = new Label("Room: " + (roomName != null ? roomName : "Multiplayer Arena"));
        roomNameLabel.getStyleClass().add("card-subtitle");

        Label promptLabel = new Label("Share this 6-character room code with your friends:");
        promptLabel.getStyleClass().add("field-label");

        // Prominent Room Code Display
        Label codeDisplay = new Label(roomCode);
        codeDisplay.getStyleClass().add("room-code-display");

        copyFeedbackLabel = new Label();
        copyFeedbackLabel.getStyleClass().add("status-label");
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
                copyFeedbackLabel.setText("Room code: " + roomCode);
                copyFeedbackLabel.setVisible(true);
            }
        });

        HBox copyBox = new HBox(12, codeDisplay, copyButton);
        copyBox.setAlignment(Pos.CENTER);

        Button enterLobbyButton = new Button("Enter Multiplayer Lobby ➔");
        enterLobbyButton.getStyleClass().add("primary-button");
        enterLobbyButton.setMaxWidth(Double.MAX_VALUE);
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
        actions.setMaxWidth(380);

        getChildren().addAll(
                heading,
                hostBadge,
                roomNameLabel,
                promptLabel,
                copyBox,
                copyFeedbackLabel,
                actions
        );
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getHostUsername() {
        return hostUsername;
    }
}
