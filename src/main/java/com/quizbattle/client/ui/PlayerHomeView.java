package com.quizbattle.client.ui;

import com.quizbattle.model.Question;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

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
    private final Label timerLabel;
    private final ProgressBar timerProgressBar;
    private final List<Button> optionButtons = new ArrayList<>();

    private Question currentQuestion;
    private int currentScore = 0;
    private boolean answeredCurrent = false;
    private Timeline countdownTimeline;
    private long deadlineMillis;

    @SuppressWarnings("this-escape")
    public PlayerHomeView(
            String username,
            String playerId,
            String hostPort,
            Runnable onJoinDefaultRoom,
            BiConsumer<String, Integer> onAnswerSubmitted,
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

        timerLabel = new Label("Time left: --");
        timerLabel.getStyleClass().add("question-timer-label");
        timerProgressBar = new ProgressBar(1);
        timerProgressBar.setMaxWidth(Double.MAX_VALUE);
        timerProgressBar.getStyleClass().add("question-timer-progress");

        HBox timerRow = new HBox(12, timerLabel, timerProgressBar);
        timerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(timerProgressBar, Priority.ALWAYS);

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

        quizSection.getChildren().addAll(
                quizHeader,
                timerRow,
                questionPromptLabel,
                optionsContainer,
                resultBanner
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
        stopQuestionTimer();
        this.currentQuestion = question;
        String progress = question.getQuestionNumber() > 0 && question.getTotalQuestions() > 0
                ? "Question " + question.getQuestionNumber() + " of " + question.getTotalQuestions()
                : "Question";
        questionMetaLabel.setText(progress + " | Points: " + question.getPointValue());
        questionPromptLabel.setText(question.getText());

        optionsContainer.getChildren().clear();
        optionButtons.clear();
        resultBanner.setVisible(false);
        resultBanner.setManaged(false);
        answeredCurrent = false;

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
                resultBanner.getStyleClass().removeAll("result-correct", "result-incorrect");
                resultBanner.setText("Submitting answer...");
                resultBanner.setVisible(true);
                resultBanner.setManaged(true);
                if (onAnswerSubmitted != null) {
                    onAnswerSubmitted.accept(question.getQuestionId(), optionIndex);
                }
            });

            optionButtons.add(optBtn);
            optionsContainer.getChildren().add(optBtn);
        }
        startQuestionTimer(question);
    }

    private void startQuestionTimer(Question question) {
        long now = System.currentTimeMillis();
        long durationMillis = question.getTimeLimitSeconds() * 1000L;
        deadlineMillis = question.getDeadlineMillis() > 0
                ? question.getDeadlineMillis()
                : now + durationMillis;
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateCountdown()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        updateCountdown();
        if (deadlineMillis > System.currentTimeMillis()) {
            countdownTimeline.play();
        } else {
            countdownTimeline = null;
        }
    }

    private void updateCountdown() {
        long remainingMillis = Math.max(0, deadlineMillis - System.currentTimeMillis());
        int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);
        int totalSeconds = Math.max(1, currentQuestion != null ? currentQuestion.getTimeLimitSeconds() : 1);
        timerLabel.setText(remainingSeconds > 0 ? "Time left: " + remainingSeconds + "s" : "Time's up");
        timerProgressBar.setProgress(Math.min(1, remainingMillis / (totalSeconds * 1000.0)));
        if (remainingMillis == 0) {
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
            optionButtons.forEach(button -> button.setDisable(true));
        }
    }

    public void stopQuestionTimer() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    /** Shows the server's verdict for this player's answer. The score shown is the server total. */
    public void displayAnswerResult(boolean accepted, String rejectReason, boolean correct, int pointsAwarded, int totalScore) {
        this.currentScore = totalScore;
        scoreBadge.setText("Score: " + totalScore + " pts");

        resultBanner.setVisible(true);
        resultBanner.setManaged(true);
        resultBanner.getStyleClass().removeAll("result-correct", "result-incorrect");

        if (!accepted) {
            String why = switch (rejectReason == null ? "" : rejectReason) {
                case "DUPLICATE" -> "you already answered this question";
                case "LATE" -> "the question was already closed";
                case "WRONG_ROOM" -> "you are not a participant of this room's quiz";
                default -> "no quiz is running";
            };
            resultBanner.setText("⚠ Answer not counted: " + why + ". Total Score: " + totalScore + " pts");
            resultBanner.getStyleClass().add("result-incorrect");
            return;
        }

        answeredCurrent = true;
        if (correct) {
            resultBanner.setText("✓ Correct! +" + pointsAwarded + " points. Total Score: " + totalScore + " pts");
            resultBanner.getStyleClass().add("result-correct");
        } else {
            resultBanner.setText("✗ Incorrect choice. Total Score: " + totalScore + " pts");
            resultBanner.getStyleClass().add("result-incorrect");
        }
    }

    /** QUESTION_CLOSED: lock the options and reveal the correct one. */
    public void onQuestionClosed(String questionId, int correctOptionIndex) {
        if (currentQuestion == null || !currentQuestion.getQuestionId().equals(questionId)) {
            return;
        }
        stopQuestionTimer();
        timerLabel.setText("Question closed");
        timerProgressBar.setProgress(0);
        for (int i = 0; i < optionButtons.size(); i++) {
            Button b = optionButtons.get(i);
            b.setDisable(true);
            if (i == correctOptionIndex) {
                b.getStyleClass().add("option-correct");
            }
        }
        if (!answeredCurrent) {
            resultBanner.setVisible(true);
            resultBanner.setManaged(true);
            resultBanner.getStyleClass().removeAll("result-correct", "result-incorrect");
            resultBanner.getStyleClass().add("result-incorrect");
            resultBanner.setText("⌛ Question closed before you answered.");
        }
    }

    public int getCurrentScore() {
        return currentScore;
    }

    public Question getCurrentQuestion() {
        return currentQuestion;
    }
}
