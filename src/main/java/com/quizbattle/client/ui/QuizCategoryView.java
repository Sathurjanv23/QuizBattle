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
 * Dedicated quiz category selection screen for the lobby host.
 */
public class QuizCategoryView extends VBox {

    public QuizCategoryView(Consumer<String> onStartQuizRequested, Runnable onBackRequested) {
        super(18);
        getStyleClass().add("welcome-card");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(620);
        setPadding(new Insets(24));

        Label title = new Label("Choose a Quiz Category");
        title.getStyleClass().add("card-heading");

        Label subtitle = new Label("Select the topic for this quiz round.");
        subtitle.getStyleClass().add("card-subtitle");

        VBox heading = new VBox(4, title, subtitle);
        heading.setAlignment(Pos.CENTER);

        ToggleGroup categoryGroup = new ToggleGroup();

        ToggleButton generalButton = createCategoryButton(
                "🌍",
                "General Trivia",
                "A mix of popular knowledge questions",
                categoryGroup);
        ToggleButton javaButton = createCategoryButton(
                "☕",
                "Java",
                "Programming concepts and Java fundamentals",
                categoryGroup);
        ToggleButton networkingButton = createCategoryButton(
                "🌐",
                "Networking",
                "Networks, protocols, and connectivity",
                categoryGroup);

        generalButton.setSelected(true);

        VBox categoryOptions = new VBox(10, generalButton, javaButton, networkingButton);
        categoryOptions.setAlignment(Pos.CENTER);
        categoryOptions.setMaxWidth(Double.MAX_VALUE);

        Region actionSpacer = new Region();
        VBox.setVgrow(actionSpacer, Priority.ALWAYS);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);

        Button backButton = new Button("Back to Lobby");
        backButton.getStyleClass().add("secondary-button");
        backButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(backButton, Priority.ALWAYS);
        backButton.setOnAction(e -> {
            if (onBackRequested != null) {
                onBackRequested.run();
            }
        });

        Button startButton = new Button("Start Quiz");
        startButton.getStyleClass().add("primary-button");
        startButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(startButton, Priority.ALWAYS);
        startButton.setStyle("-fx-font-size: 15px; -fx-padding: 12px 24px;");
        startButton.setOnAction(e -> {
            ToggleButton selected = (ToggleButton) categoryGroup.getSelectedToggle();
            if (selected != null && onStartQuizRequested != null) {
                onStartQuizRequested.accept((String) selected.getUserData());
            }
        });

        actions.getChildren().addAll(backButton, startButton);

        getChildren().addAll(heading, categoryOptions, actionSpacer, actions);
    }

    private ToggleButton createCategoryButton(
            String icon,
            String name,
            String description,
            ToggleGroup categoryGroup) {
        ToggleButton button = new ToggleButton();
        button.getStyleClass().add("category-card");
        button.setToggleGroup(categoryGroup);
        button.setUserData(name);
        button.setMaxWidth(Double.MAX_VALUE);

        HBox content = new HBox(14);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMouseTransparent(true);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("category-icon");

        VBox text = new VBox(3);
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("category-name");
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("category-description");
        text.getChildren().addAll(nameLabel, descriptionLabel);

        content.getChildren().addAll(iconLabel, text);
        button.setGraphic(content);
        return button;
    }
}
