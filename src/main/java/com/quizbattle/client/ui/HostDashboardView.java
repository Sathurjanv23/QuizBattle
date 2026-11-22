package com.quizbattle.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Screen 1: Host / Player Dashboard View.
 * Displays player identity, connection status, room creation trigger,
 * join room navigation, quick play, and error feedback.
 */
public class HostDashboardView extends VBox {

    private final String username;
    private final String playerId;
    private final String hostPort;

    private final Label statusFeedbackLabel;
    private final ProgressIndicator progressSpinner;
    private final Button createRoomButton;
    private final Button joinRoomButton;
    private final Button quickPlayButton;

    @SuppressWarnings("this-escape")
    public HostDashboardView(
            String username,
            String playerId,
            String hostPort,
            Runnable onCreateRoomRequested,
            Runnable onJoinRoomNavigationRequested,
            Runnable onQuickPlayRequested,
            Runnable onDisconnectRequested) {
        super(20);
        this.username = username;
        this.playerId = playerId;
        this.hostPort = hostPort;

        getStyleClass().add("welcome-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(560);
        setPadding(new Insets(28));

        // 1. Top Header & Identity Bar
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("player-header-bar");
        topBar.setPadding(new Insets(10, 16, 10, 16));

        Label playerLabel = new Label("👤 " + username + " (" + playerId + ")");
        playerLabel.getStyleClass().add("player-identity-label");

        Label connectionBadge = new Label("🟢 TCP: " + hostPort);
        connectionBadge.getStyleClass().add("connected-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.getStyleClass().add("danger-button");
        disconnectButton.setOnAction(e -> {
            if (onDisconnectRequested != null) {
                onDisconnectRequested.run();
            }
        });

        topBar.getChildren().addAll(playerLabel, connectionBadge, spacer, disconnectButton);

        // 2. Dashboard Card Titles
        Label heading = new Label("Multiplayer Battle Dashboard");
        heading.getStyleClass().add("card-heading");

        Label subtitle = new Label("Create a private room or enter an existing battle room code");
        subtitle.getStyleClass().add("card-subtitle");

        // 3. Status & Error Feedback Banner
        progressSpinner = new ProgressIndicator();
        progressSpinner.setPrefSize(20, 20);
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);

        statusFeedbackLabel = new Label();
        statusFeedbackLabel.getStyleClass().add("error-box");
        statusFeedbackLabel.setWrapText(true);
        statusFeedbackLabel.setVisible(false);
        statusFeedbackLabel.setManaged(false);

        HBox statusRow = new HBox(10, progressSpinner, statusFeedbackLabel);
        statusRow.setAlignment(Pos.CENTER);

        // 4. Action Buttons
        createRoomButton = new Button("🎮 Create Multiplayer Room");
        createRoomButton.getStyleClass().add("primary-button");
        createRoomButton.setMaxWidth(Double.MAX_VALUE);
        createRoomButton.setOnAction(e -> {
            setLoading(true, "Requesting server to create a new multiplayer room...");
            if (onCreateRoomRequested != null) {
                onCreateRoomRequested.run();
            }
        });

        joinRoomButton = new Button("🚪 Join Room by Code");
        joinRoomButton.getStyleClass().add("secondary-button");
        joinRoomButton.setMaxWidth(Double.MAX_VALUE);
        joinRoomButton.setOnAction(e -> {
            if (onJoinRoomNavigationRequested != null) {
                onJoinRoomNavigationRequested.run();
            }
        });

        quickPlayButton = new Button("🎯 Quick Play (Default Room)");
        quickPlayButton.getStyleClass().add("secondary-button");
        quickPlayButton.setMaxWidth(Double.MAX_VALUE);
        quickPlayButton.setOnAction(e -> {
            if (onQuickPlayRequested != null) {
                onQuickPlayRequested.run();
            }
        });

        VBox buttonBox = new VBox(12, createRoomButton, joinRoomButton, quickPlayButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setMaxWidth(420);

        getChildren().addAll(
                topBar,
                heading,
                subtitle,
                statusRow,
                buttonBox
        );
    }

    public void showError(String errorMessage) {
        setLoading(false, null);
        statusFeedbackLabel.setText("⚠️ " + errorMessage);
        statusFeedbackLabel.setVisible(true);
        statusFeedbackLabel.setManaged(true);
    }

    public void clearError() {
        statusFeedbackLabel.setText("");
        statusFeedbackLabel.setVisible(false);
        statusFeedbackLabel.setManaged(false);
    }

    public void setLoading(boolean loading, String message) {
        progressSpinner.setVisible(loading);
        progressSpinner.setManaged(loading);
        createRoomButton.setDisable(loading);
        joinRoomButton.setDisable(loading);
        quickPlayButton.setDisable(loading);
        if (loading && message != null) {
            statusFeedbackLabel.setText("⏳ " + message);
            statusFeedbackLabel.setVisible(true);
            statusFeedbackLabel.setManaged(true);
        } else if (!loading) {
            clearError();
        }
    }

    public String getUsername() {
        return username;
    }

    public String getPlayerId() {
        return playerId;
    }
}
