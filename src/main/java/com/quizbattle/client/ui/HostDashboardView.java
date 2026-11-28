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
 * Screen 4: Host / Player Dashboard View.
 * Displays player identity, connection info, and two large interactive battle action cards
 * (Create a Battle vs Join a Battle) plus instant Quick Play.
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

        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(680);
        setPadding(new Insets(24, 32, 28, 32));

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

        // 2. Dashboard Titles
        Label heading = new Label("Multiplayer Battle Arena");
        heading.getStyleClass().add("card-heading");

        Label subtitle = new Label("Host your own private tournament or join a friend's room code");
        subtitle.getStyleClass().add("card-subtitle");

        VBox titleBox = new VBox(4, heading, subtitle);
        titleBox.setAlignment(Pos.CENTER);

        // 3. Status & Error Feedback Banner
        progressSpinner = new ProgressIndicator();
        progressSpinner.setPrefSize(18, 18);
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);

        statusFeedbackLabel = new Label();
        statusFeedbackLabel.getStyleClass().add("error-box");
        statusFeedbackLabel.setWrapText(true);
        statusFeedbackLabel.setVisible(false);
        statusFeedbackLabel.setManaged(false);

        HBox statusRow = new HBox(10, progressSpinner, statusFeedbackLabel);
        statusRow.setAlignment(Pos.CENTER);

        // 4. Two Large Action Cards: CREATE A BATTLE vs JOIN A BATTLE
        HBox actionCardsBox = new HBox(18);
        actionCardsBox.setAlignment(Pos.CENTER);

        // Card A: Create Room
        VBox createCard = new VBox(12);
        createCard.getStyleClass().add("battle-action-card");
        createCard.setAlignment(Pos.CENTER);
        HBox.setHgrow(createCard, Priority.ALWAYS);

        Label createIcon = new Label("👑");
        createIcon.setStyle("-fx-font-size: 32px;");

        Label createTitle = new Label("CREATE A BATTLE");
        createTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

        Label createDesc = new Label("Host a private multiplayer quiz, pick categories, and invite friends.");
        createDesc.getStyleClass().add("card-subtitle");
        createDesc.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        createDesc.setWrapText(true);
        createDesc.setPrefHeight(42);

        createRoomButton = new Button("Host New Room ➔");
        createRoomButton.getStyleClass().add("primary-button");
        createRoomButton.setMaxWidth(Double.MAX_VALUE);
        createRoomButton.setOnAction(e -> {
            setLoading(true);
            clearError();
            if (onCreateRoomRequested != null) {
                onCreateRoomRequested.run();
            }
        });

        createCard.getChildren().addAll(createIcon, createTitle, createDesc, createRoomButton);

        // Card B: Join Room
        VBox joinCard = new VBox(12);
        joinCard.getStyleClass().add("battle-action-card");
        joinCard.setAlignment(Pos.CENTER);
        HBox.setHgrow(joinCard, Priority.ALWAYS);

        Label joinIcon = new Label("🎯");
        joinIcon.setStyle("-fx-font-size: 32px;");

        Label joinTitle = new Label("JOIN A BATTLE");
        joinTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

        Label joinDesc = new Label("Enter a 6-character room code to join an existing game lobby.");
        joinDesc.getStyleClass().add("card-subtitle");
        joinDesc.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        joinDesc.setWrapText(true);
        joinDesc.setPrefHeight(42);

        joinRoomButton = new Button("Enter Room Code ➔");
        joinRoomButton.getStyleClass().add("purple-button");
        joinRoomButton.setMaxWidth(Double.MAX_VALUE);
        joinRoomButton.setOnAction(e -> {
            clearError();
            if (onJoinRoomNavigationRequested != null) {
                onJoinRoomNavigationRequested.run();
            }
        });

        joinCard.getChildren().addAll(joinIcon, joinTitle, joinDesc, joinRoomButton);

        actionCardsBox.getChildren().addAll(createCard, joinCard);

        // 5. Quick Play & Practice Action (Centered below cards)
        VBox quickPlayBox = new VBox(8);
        quickPlayBox.setAlignment(Pos.CENTER);
        quickPlayBox.setPadding(new Insets(10, 0, 0, 0));

        quickPlayButton = new Button("⚡ Quick Play (Practice Arena)");
        quickPlayButton.getStyleClass().add("secondary-button");
        quickPlayButton.setMaxWidth(380);
        quickPlayButton.setStyle("-fx-font-size: 13px; -fx-padding: 10px 20px;");
        quickPlayButton.setOnAction(e -> {
            setLoading(true);
            clearError();
            if (onQuickPlayRequested != null) {
                onQuickPlayRequested.run();
            }
        });

        Label quickPlayHint = new Label("Instantly play trivia in the default community room without waiting for a lobby.");
        quickPlayHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        quickPlayBox.getChildren().addAll(quickPlayButton, quickPlayHint);

        getChildren().addAll(topBar, titleBox, statusRow, actionCardsBox, quickPlayBox);
    }

    public void setLoading(boolean loading) {
        progressSpinner.setVisible(loading);
        progressSpinner.setManaged(loading);
        createRoomButton.setDisable(loading);
        joinRoomButton.setDisable(loading);
        quickPlayButton.setDisable(loading);
    }

    public void showError(String message) {
        setLoading(false);
        statusFeedbackLabel.setText("⚠ " + message);
        statusFeedbackLabel.setVisible(true);
        statusFeedbackLabel.setManaged(true);
    }

    public void clearError() {
        statusFeedbackLabel.setVisible(false);
        statusFeedbackLabel.setManaged(false);
        statusFeedbackLabel.setText("");
    }
}
