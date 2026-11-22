package com.quizbattle.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Screen 1: Player Welcome View.
 * Captures the player's username before transitioning to the manual connect screen.
 */
public class WelcomeView extends VBox {

    private final TextField usernameField;
    private final Label validationLabel;

    @SuppressWarnings("this-escape")
    public WelcomeView(Consumer<String> onUsernameSubmitted) {
        super(16);
        getStyleClass().add("welcome-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(440);
        setPadding(new Insets(32));

        Label heading = new Label("QuizBattle 2.0");
        heading.getStyleClass().add("card-heading");

        Label subtitle = new Label("Multiplayer Network Trivia Arena");
        subtitle.getStyleClass().add("card-subtitle");

        Label promptLabel = new Label("Choose your player username:");
        promptLabel.getStyleClass().add("field-label");

        usernameField = new TextField();
        usernameField.setPromptText("e.g. CyberNinja");
        usernameField.getStyleClass().add("input-field");

        validationLabel = new Label();
        validationLabel.getStyleClass().add("validation-error-label");
        validationLabel.setVisible(false);

        Button continueButton = new Button("Continue to Connect ➔");
        continueButton.getStyleClass().add("primary-button");
        continueButton.setMaxWidth(Double.MAX_VALUE);

        continueButton.setOnAction(e -> {
            String name = usernameField.getText().trim();
            if (name.isBlank()) {
                validationLabel.setText("Please enter a valid username.");
                validationLabel.setVisible(true);
            } else {
                validationLabel.setVisible(false);
                if (onUsernameSubmitted != null) {
                    onUsernameSubmitted.accept(name);
                }
            }
        });

        usernameField.setOnAction(e -> continueButton.fire());

        getChildren().addAll(heading, subtitle, promptLabel, usernameField, validationLabel, continueButton);
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }
}
