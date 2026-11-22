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
 * Screen 3: Join Room View.
 * Captures and validates user-entered room codes, presents loading and error states,
 * and initiates authoritative room join network requests.
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
        getStyleClass().add("welcome-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(480);
        setPadding(new Insets(32));

        Label heading = new Label("Join Multiplayer Room");
        heading.getStyleClass().add("card-heading");

        Label subtitle = new Label("Enter the 6-character room code shared by the host");
        subtitle.getStyleClass().add("card-subtitle");

        Label playerGreeting = new Label("Joining as: " + username);
        playerGreeting.getStyleClass().add("player-badge");

        Label fieldLabel = new Label("Room Code:");
        fieldLabel.getStyleClass().add("field-label");

        roomCodeField = new TextField();
        roomCodeField.setPromptText("e.g. QB42KD");
        roomCodeField.getStyleClass().add("input-field");
        roomCodeField.setAlignment(Pos.CENTER);
        roomCodeField.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-alignment: center;");

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

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-box");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        progressSpinner = new ProgressIndicator();
        progressSpinner.setPrefSize(20, 20);
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);

        HBox statusRow = new HBox(8, progressSpinner, errorLabel);
        statusRow.setAlignment(Pos.CENTER);

        joinButton = new Button("Join Room ➔");
        joinButton.getStyleClass().add("primary-button");
        joinButton.setMaxWidth(Double.MAX_VALUE);

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
                showError("Room code is too short.");
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
        actions.setMaxWidth(380);

        getChildren().addAll(
                heading,
                subtitle,
                playerGreeting,
                fieldLabel,
                roomCodeField,
                statusRow,
                actions
        );
    }

    public void showError(String errorMessage) {
        setLoading(false);
        errorLabel.setText("⚠️ " + errorMessage);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public void setLoading(boolean loading) {
        progressSpinner.setVisible(loading);
        progressSpinner.setManaged(loading);
        joinButton.setDisable(loading);
        backButton.setDisable(loading);
        roomCodeField.setDisable(loading);
    }
}
