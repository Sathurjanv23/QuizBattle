package com.quizbattle.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Screen 1: Player Welcome View.
 * Captures the player's username before transitioning to server discovery.
 * Redesigned with modern gaming aesthetics, badge highlights, and responsive layout.
 */
public class WelcomeView extends VBox {

    private final TextField usernameField;
    private final Label validationLabel;

    @SuppressWarnings("this-escape")
    public WelcomeView(Consumer<String> onUsernameSubmitted) {
        super(20);
        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(520);
        setPadding(new Insets(36, 40, 36, 40));

        // 1. Branding Header
        Label gameIcon = new Label("⚔️");
        gameIcon.setStyle("-fx-font-size: 42px;");

        Label heading = new Label("QUIZBATTLE 2.0");
        heading.getStyleClass().add("hero-title");

        Label tagline = new Label("Challenge Friends. Battle in Real Time.");
        tagline.getStyleClass().add("hero-tagline");

        Label description = new Label("The ultimate university multiplayer network trivia arena.\nTest your knowledge across Java, Networking, and Trivia.");
        description.getStyleClass().add("card-subtitle");
        description.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        description.setWrapText(true);

        VBox brandBox = new VBox(6, gameIcon, heading, tagline, description);
        brandBox.setAlignment(Pos.CENTER);

        // Feature highlight pills
        HBox pillsBox = new HBox(8);
        pillsBox.setAlignment(Pos.CENTER);
        Label pill1 = new Label("⚡ Fast TCP");
        pill1.getStyleClass().add("pill-badge");
        Label pill2 = new Label("🎮 Multi-Lobbies");
        pill2.getStyleClass().add("pill-badge");
        Label pill3 = new Label("🏆 Live Ranks");
        pill3.getStyleClass().add("pill-badge");
        pillsBox.getChildren().addAll(pill1, pill2, pill3);

        // 2. Input Section
        VBox inputSection = new VBox(8);
        inputSection.setAlignment(Pos.CENTER_LEFT);
        inputSection.setMaxWidth(Double.MAX_VALUE);

        Label promptLabel = new Label("ENTER YOUR PLAYER USERNAME");
        promptLabel.getStyleClass().add("field-label");

        usernameField = new TextField();
        usernameField.setPromptText("e.g. CyberKnight, Neo, AdaLovelace");
        usernameField.getStyleClass().add("input-field");
        usernameField.setMaxWidth(Double.MAX_VALUE);

        validationLabel = new Label();
        validationLabel.getStyleClass().add("validation-error-label");
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);

        inputSection.getChildren().addAll(promptLabel, usernameField, validationLabel);

        // 3. Action Button
        Button continueButton = new Button("Enter Quiz Arena ➔");
        continueButton.getStyleClass().add("primary-button");
        continueButton.setMaxWidth(Double.MAX_VALUE);
        continueButton.setStyle("-fx-font-size: 15px; -fx-padding: 14px 24px;");

        continueButton.setOnAction(e -> {
            String name = usernameField.getText().trim();
            if (name.isBlank()) {
                validationLabel.setText("⚠ Please enter a username to continue.");
                validationLabel.setVisible(true);
                validationLabel.setManaged(true);
            } else {
                validationLabel.setVisible(false);
                validationLabel.setManaged(false);
                if (onUsernameSubmitted != null) {
                    onUsernameSubmitted.accept(name);
                }
            }
        });

        usernameField.setOnAction(e -> continueButton.fire());

        // 4. Subtle Footer Note
        Label versionNote = new Label("v2.0 • University Network Programming Edition");
        versionNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        getChildren().addAll(brandBox, pillsBox, inputSection, continueButton, versionNote);
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }
}
