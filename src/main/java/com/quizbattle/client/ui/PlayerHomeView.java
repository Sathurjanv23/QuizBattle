package com.quizbattle.client.ui;

import com.quizbattle.model.Question;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Screen 3: Player Home & Basic Quiz View.
 * Displays player connection state, default room join trigger,
 * active trivia question display, multiple-choice option submission,
 * and immediate server answer feedback.
 */
public class PlayerHomeView extends VBox {

    private final String username;
    private final String playerId;
    private final String hostPort;

    private final Label scoreBadge;
    private final Label roomStatusBadge;
    private final VBox lobbySection;
    private final VBox quizSection;

    // Quiz Display Controls
    private final Label questionMetaLabel;
    private final Label questionPromptLabel;
    private final VBox optionsContainer;
    private final Label resultBanner;
    private final Button nextQuestionButton;
    private final List<Button> optionButtons = new ArrayList<>();

    private Question currentQuestion;
    private int currentScore = 0;

    @SuppressWarnings("this-escape")
    public PlayerHomeView(
            String username,
            String playerId,
            String hostPort,
            Runnable onJoinDefaultRoom,
            BiConsumer<String, Integer> onAnswerSubmitted,
            Runnable onNextQuestionRequested,
            Runnable onDisconnectRequested) {
        super(16);
        this.username = username;
        this.playerId = playerId;
        this.hostPort = hostPort;

        getStyleClass().add("player-home-container");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(700);
        setPadding(new Insets(20));

        // 1. Top Player & Connection Bar
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("player-header-bar");
        topBar.setPadding(new Insets(10, 16, 10, 16));

        Label playerLabel = new Label("👤 " + username + " (" + playerId + ")");
        playerLabel.getStyleClass().add("player-identity-label");

        Label connectionBadge = new Label("TCP: " + hostPort);
        connectionBadge.getStyleClass().add("connected-badge");

        scoreBadge = new Label("Score: 0 pts");
        scoreBadge.getStyleClass().add("score-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.getStyleClass().add("danger-button");
        disconnectButton.setOnAction(e -> {
            if (onDisconnectRequested != null) {
                onDisconnectRequested.run();
            }
        });

        topBar.getChildren().addAll(playerLabel, connectionBadge, scoreBadge, spacer, disconnectButton);

        // 2. Room Lobby Section
        lobbySection = new VBox(12);
        lobbySection.getStyleClass().add("welcome-card");
        lobbySection.setAlignment(Pos.CENTER);
        lobbySection.setPadding(new Insets(24));

        Label lobbyTitle = new Label("Quiz Arena Lobby");
        lobbyTitle.getStyleClass().add("card-heading");

        Label lobbyDesc = new Label("Default Room: General Trivia Arena (room-default)");
        lobbyDesc.getStyleClass().add("card-subtitle");

        Button joinRoomButton = new Button("🎯 Join Default Room");
        joinRoomButton.getStyleClass().add("primary-button");
        joinRoomButton.setOnAction(e -> {
            joinRoomButton.setDisable(true);
            if (onJoinDefaultRoom != null) {
                onJoinDefaultRoom.run();
            }
        });

        lobbySection.getChildren().addAll(lobbyTitle, lobbyDesc, joinRoomButton);

        // 3. Quiz Section (hidden until room joined)
        quizSection = new VBox(16);
        quizSection.getStyleClass().add("welcome-card");
        quizSection.setAlignment(Pos.TOP_LEFT);
        quizSection.setPadding(new Insets(24));
        quizSection.setVisible(false);
        quizSection.setManaged(false);

        HBox quizHeader = new HBox(10);
        quizHeader.setAlignment(Pos.CENTER_LEFT);

        roomStatusBadge = new Label("Room: General Trivia Arena");
        roomStatusBadge.getStyleClass().add("room-badge");

        Region quizSpacer = new Region();
        HBox.setHgrow(quizSpacer, Priority.ALWAYS);

        questionMetaLabel = new Label("Question Loading...");
        questionMetaLabel.getStyleClass().add("field-label");

        quizHeader.getChildren().addAll(roomStatusBadge, quizSpacer, questionMetaLabel);

        questionPromptLabel = new Label("Waiting for active question from server...");
        questionPromptLabel.getStyleClass().add("question-text");
        questionPromptLabel.setWrapText(true);

        optionsContainer = new VBox(10);
        optionsContainer.setAlignment(Pos.CENTER);

        // Result banner
        resultBanner = new Label();
        resultBanner.setWrapText(true);
        resultBanner.setVisible(false);
        resultBanner.setManaged(false);

        // Next Question action row
        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        nextQuestionButton = new Button("Next Question ➔");
        nextQuestionButton.getStyleClass().add("primary-button");
        nextQuestionButton.setVisible(false);
        nextQuestionButton.setManaged(false);
        nextQuestionButton.setOnAction(e -> {
            nextQuestionButton.setDisable(true);
            if (onNextQuestionRequested != null) {
                onNextQuestionRequested.run();
            }
        });

        actionRow.getChildren().add(nextQuestionButton);

        quizSection.getChildren().addAll(
                quizHeader,
                questionPromptLabel,
                optionsContainer,
                resultBanner,
                actionRow
        );

        getChildren().addAll(topBar, lobbySection, quizSection);
    }

    public void onRoomJoined(String roomId, String roomName) {
        lobbySection.setVisible(false);
        lobbySection.setManaged(false);
        quizSection.setVisible(true);
        quizSection.setManaged(true);
        roomStatusBadge.setText("Room: " + roomName + " (" + roomId + ")");
    }

    public void displayQuestion(Question question, BiConsumer<String, Integer> onAnswerSubmitted) {
        this.currentQuestion = question;
        questionMetaLabel.setText("Question ID: " + question.getQuestionId() +
                " | Points: " + question.getPointValue() +
                " | Time: " + question.getTimeLimitSeconds() + "s");
        questionPromptLabel.setText(question.getText());

        optionsContainer.getChildren().clear();
        optionButtons.clear();
        resultBanner.setVisible(false);
        resultBanner.setManaged(false);
        nextQuestionButton.setVisible(false);
        nextQuestionButton.setManaged(false);
        nextQuestionButton.setDisable(false);

        List<String> options = question.getOptions();
        char labelChar = 'A';
        for (int i = 0; i < options.size(); i++) {
            final int optionIndex = i;
            String text = labelChar + ".  " + options.get(i);
            labelChar++;

            Button optBtn = new Button(text);
            optBtn.getStyleClass().add("option-button");
            optBtn.setMaxWidth(Double.MAX_VALUE);
            optBtn.setAlignment(Pos.CENTER_LEFT);

            optBtn.setOnAction(e -> {
                // Disable all option buttons to prevent duplicate answers
                for (Button b : optionButtons) {
                    b.setDisable(true);
                }
                optBtn.getStyleClass().add("option-selected");
                if (onAnswerSubmitted != null) {
                    onAnswerSubmitted.accept(question.getQuestionId(), optionIndex);
                }
            });

            optionButtons.add(optBtn);
            optionsContainer.getChildren().add(optBtn);
        }
    }

    public void displayAnswerResult(boolean correct, int pointsAwarded, int totalScore) {
        this.currentScore = totalScore;
        scoreBadge.setText("Score: " + totalScore + " pts");

        resultBanner.setVisible(true);
        resultBanner.setManaged(true);
        resultBanner.getStyleClass().removeAll("result-correct", "result-incorrect");

        if (correct) {
            resultBanner.setText("✓ Correct! +" + pointsAwarded + " points. Total Score: " + totalScore + " pts");
            resultBanner.getStyleClass().add("result-correct");
        } else {
            resultBanner.setText("✗ Incorrect choice. Total Score: " + totalScore + " pts");
            resultBanner.getStyleClass().add("result-incorrect");
        }

        nextQuestionButton.setVisible(true);
        nextQuestionButton.setManaged(true);
        nextQuestionButton.setDisable(false);
    }

    public int getCurrentScore() {
        return currentScore;
    }

    public Question getCurrentQuestion() {
        return currentQuestion;
    }
}
