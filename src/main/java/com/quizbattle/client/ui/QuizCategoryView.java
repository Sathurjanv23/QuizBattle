package com.quizbattle.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Screen 8: Dedicated Quiz Category Selection Screen for the lobby host.
 * Renders high-contrast selectable category cards (Java, Networking, General Trivia)
 * with clear descriptions and start actions.
 */
public class QuizCategoryView extends VBox {

    public QuizCategoryView(Consumer<String> onStartQuizRequested, Runnable onBackRequested) {
        super(20);
        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(640);
        setPadding(new Insets(28, 36, 28, 36));

        Label icon = new Label("🎮");
        icon.setStyle("-fx-font-size: 32px;");

        Label title = new Label("Choose Quiz Battle Topic");
        title.getStyleClass().add("card-heading");

        Label subtitle = new Label("Select the authoritative question bank for this multiplayer round.");
        subtitle.getStyleClass().add("card-subtitle");

        VBox heading = new VBox(4, icon, title, subtitle);
        heading.setAlignment(Pos.CENTER);

        ToggleGroup categoryGroup = new ToggleGroup();

        ToggleButton javaButton = createCategoryButton(
                "☕",
                "#F59E0B",
                "Java",
                "Java Fundamentals & OOP",
                "Core syntax, threading, collections, and Java NIO",
                categoryGroup);

        ToggleButton networkingButton = createCategoryButton(
                "🌐",
                "#38BDF8",
                "Networking",
                "Computer Networks",
                "TCP sockets, UDP datagrams, OSI model, and framing",
                categoryGroup);

        ToggleButton generalButton = createCategoryButton(
                "🌍",
                "#10B981",
                "General Trivia",
                "General Knowledge",
                "Science, computing history, and popular technology",
                categoryGroup);

        javaButton.setSelected(true); // Default to Java Programming

        VBox categoryOptions = new VBox(12, javaButton, networkingButton, generalButton);
        categoryOptions.setAlignment(Pos.CENTER);
        categoryOptions.setMaxWidth(Double.MAX_VALUE);

        HBox actions = new HBox(14);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(10, 0, 0, 0));

        Button backButton = new Button("⬅ Back to Lobby");
        backButton.getStyleClass().add("secondary-button");
        backButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(backButton, Priority.ALWAYS);
        backButton.setOnAction(e -> {
            if (onBackRequested != null) {
                onBackRequested.run();
            }
        });

        Button startButton = new Button("Start Quiz Battle ➔");
        startButton.getStyleClass().add("primary-button");
        startButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(startButton, Priority.ALWAYS);
        startButton.setStyle("-fx-font-size: 15px; -fx-padding: 13px 26px;");
        startButton.setOnAction(e -> {
            ToggleButton selected = (ToggleButton) categoryGroup.getSelectedToggle();
            if (selected != null && onStartQuizRequested != null) {
                onStartQuizRequested.accept((String) selected.getUserData());
            }
        });

        actions.getChildren().addAll(backButton, startButton);

        getChildren().addAll(heading, categoryOptions, actions);
    }

    private ToggleButton createCategoryButton(
            String icon,
            String accentColorHex,
            String categoryKey,
            String name,
            String description,
            ToggleGroup categoryGroup) {
        ToggleButton button = new ToggleButton();
        button.getStyleClass().add("category-card");
        button.setToggleGroup(categoryGroup);
        button.setUserData(categoryKey);
        button.setMaxWidth(Double.MAX_VALUE);

        HBox content = new HBox(16);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMouseTransparent(true);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 28px; -fx-background-color: rgba(255, 255, 255, 0.08); -fx-background-radius: 8px; -fx-padding: 6px 12px;");

        VBox text = new VBox(4);
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("category-name");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("category-description");
        descriptionLabel.setWrapText(true);

        text.getChildren().addAll(nameLabel, descriptionLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label tag = new Label("10 Questions");
        tag.getStyleClass().add("pill-badge");

        content.getChildren().addAll(iconLabel, text, spacer, tag);
        button.setGraphic(content);
        return button;
    }
}
