package com.quizbattle.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Dedicated category-selection screen shown before a host starts a quiz.
 */
public class CategorySelectionView extends VBox {

    private final Button startQuizButton;
    private final Label selectionLabel;
    private String selectedCategory;

    @SuppressWarnings("this-escape")
    public CategorySelectionView(
            String roomName,
            String currentUsername,
            Consumer<String> onStartQuizRequested,
            Runnable onBackRequested) {
        super(18);
        getStyleClass().add("welcome-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(560);
        setPadding(new Insets(30));

        Label eyebrow = new Label("QUIZ SETUP");
        eyebrow.getStyleClass().add("category-eyebrow");

        Label heading = new Label("Select a quiz category");
        heading.getStyleClass().add("card-heading");

        Label subtitle = new Label("Choose a topic for " + roomName + " before the battle begins.");
        subtitle.getStyleClass().add("card-subtitle");
        subtitle.setWrapText(true);

        selectionLabel = new Label("No category selected");
        selectionLabel.getStyleClass().add("category-selection-label");

        HBox categoryChoices = new HBox(12);
        categoryChoices.setAlignment(Pos.CENTER);
        categoryChoices.setMaxWidth(Double.MAX_VALUE);

        addCategoryButton(categoryChoices, "General Trivia");
        addCategoryButton(categoryChoices, "Java");
        addCategoryButton(categoryChoices, "Networking");

        startQuizButton = new Button("🚀 Start Quiz");
        startQuizButton.getStyleClass().add("primary-button");
        startQuizButton.setMaxWidth(Double.MAX_VALUE);
        startQuizButton.setDisable(true);
        startQuizButton.setOnAction(e -> {
            if (selectedCategory != null && onStartQuizRequested != null) {
                startQuizButton.setDisable(true);
                onStartQuizRequested.accept(selectedCategory);
            }
        });

        Button backButton = new Button("← Back to Lobby");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(e -> {
            if (onBackRequested != null) {
                onBackRequested.run();
            }
        });

        Label hostLabel = new Label("Host: " + currentUsername);
        hostLabel.getStyleClass().add("status-label");

        getChildren().addAll(eyebrow, heading, subtitle, selectionLabel, categoryChoices, startQuizButton, backButton, hostLabel);
    }

    private void addCategoryButton(HBox choices, String category) {
        Button button = new Button(category);
        button.getStyleClass().add("category-choice");
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setOnAction(e -> {
            selectedCategory = category;
            selectionLabel.setText("Selected: " + category);
            startQuizButton.setDisable(false);
            choices.getChildren().forEach(choice -> choice.getStyleClass().remove("category-choice-selected"));
            button.getStyleClass().add("category-choice-selected");
        });
        choices.getChildren().add(button);
    }
}
