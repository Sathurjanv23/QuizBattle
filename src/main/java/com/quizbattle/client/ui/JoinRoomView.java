package com.quizbattle.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Screen 6: Join Room View.
 * Captures and validates user-entered room codes with automatic uppercase formatting,
 * responsive loading feedback, and server join initiation.
 */
public class JoinRoomView extends VBox {

    private final TextField roomCodeField;
    private final Label errorLabel;
    private final ProgressIndicator progressSpinner;
    private final Button joinButton;
    private final Button backButton;

    @SuppressWarnings("this-escape")
    public JoinRoomView(String username, Consumer<String> onJoinRequested, Runnable onBackRequested) {
        super(18);
        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(500);
        setPadding(new Insets(32, 40, 36, 40));

        // 1. Heading & Greeting
        Label icon = new Label("🎯");
        icon.setStyle("-fx-font-size: 36px;");

        Label heading = new Label("Join Battle Room");
        heading.getStyleClass().add("card-heading");

        Label subtitle = new Label("Enter the 6-character room code shared by your battle host.");
        subtitle.getStyleClass().add("card-subtitle");
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        subtitle.setWrapText(true);

        Label playerGreeting = new Label("Joining as: " + username);
        playerGreeting.getStyleClass().add("player-badge");

        VBox headerBox = new VBox(6, icon, heading, playerGreeting, subtitle);
        headerBox.setAlignment(Pos.CENTER);

        // 2. Input Section
        VBox inputSection = new VBox(8);
        inputSection.setAlignment(Pos.CENTER);

        Label fieldLabel = new Label("ROOM CODE");
        fieldLabel.getStyleClass().add("field-label");

        roomCodeField = new TextField();
        roomCodeField.setPromptText("e.g. QB42KD");
        roomCodeField.getStyleClass().add("input-field");
        roomCodeField.setAlignment(Pos.CENTER);
        roomCodeField.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-font-family: 'Consolas', monospace; -fx-alignment: center; -fx-padding: 10px 18px;");

        // Auto-uppercase text entry and trim
        roomCodeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String upper = newVal.toUpperCase().replaceAll("\\s+", "");
                if (upper.length() > 12) {
                    upper = upper.substring(0, 12);
                }
                if (!upper.equals(newVal)) {
                    roomCodeField.setText(upper);
                }
            }
        });

        inputSection.getChildren().addAll(fieldLabel, roomCodeField);

        // 3. Status & Error Box
        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-box");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        progressSpinner = new ProgressIndicator();
        progressSpinner.setPrefSize(18, 18);
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);

        HBox statusRow = new HBox(8, progressSpinner, errorLabel);
        statusRow.setAlignment(Pos.CENTER);

        // 4. Action Buttons
        joinButton = new Button("Join Battle Room ➔");
        joinButton.getStyleClass().add("primary-button");
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.setStyle("-fx-font-size: 15px; -fx-padding: 13px 24px;");

        backButton = new Button("⬅ Back to Dashboard");
        backButton.getStyleClass().add("secondary-button");
        backButton.setMaxWidth(Double.MAX_VALUE);

        joinButton.setOnAction(e -> {
            String code = roomCodeField.getText().trim();
            if (code.isBlank()) {
                showError("Please enter a room code.");
                return;
            }
            if (code.length() < 3) {
                showError("Room code is too short (must be at least 3 characters).");
                return;
            }
            clearError();
            setLoading(true);
            if (onJoinRequested != null) {
                onJoinRequested.accept(code);
            }
        });

        roomCodeField.setOnAction(e -> joinButton.fire());

        backButton.setOnAction(e -> {
            if (onBackRequested != null) {
                onBackRequested.run();
            }
        });

        VBox actions = new VBox(10, joinButton, backButton);
        actions.setAlignment(Pos.CENTER);

        getChildren().addAll(headerBox, inputSection, statusRow, actions);
    }

    public void setLoading(boolean loading) {
        progressSpinner.setVisible(loading);
        progressSpinner.setManaged(loading);
        joinButton.setDisable(loading);
        roomCodeField.setDisable(loading);
    }

    public void showError(String message) {
        setLoading(false);
        errorLabel.setText("⚠ " + message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
    }
}
