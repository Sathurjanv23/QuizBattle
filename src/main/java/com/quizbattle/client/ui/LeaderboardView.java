package com.quizbattle.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Member 5: Live Leaderboard screen, shown after each question closes.
 *
 * <p>Renders the server's authoritative LEADERBOARD snapshot (rank, username, correct count, score,
 * disconnected marker). The client never computes or changes scores. The host sees the
 * "Next Question" control; other players see a waiting hint.</p>
 */
public class LeaderboardView extends VBox {

    private final String myPlayerId;
    private final Label progressLabel;
    private final VBox rowsBox;

    @SuppressWarnings("this-escape")
    public LeaderboardView(String myPlayerId, boolean canAdvance, Runnable onNextQuestion) {
        super(14);
        this.myPlayerId = myPlayerId;

        getStyleClass().add("welcome-card");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(640);
        setMaxHeight(Region.USE_PREF_SIZE);
        setPadding(new Insets(24));

        Label title = new Label("📊 Live Leaderboard");
        title.getStyleClass().add("card-heading");

        progressLabel = new Label("Waiting for scores...");
        progressLabel.getStyleClass().add("card-subtitle");

        rowsBox = new VBox(8);
        ScrollPane scroll = new ScrollPane(rowsBox);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(300);
        scroll.getStyleClass().add("leaderboard-scroll");

        Region header = headerRow();

        VBox footer = new VBox(8);
        footer.setAlignment(Pos.CENTER_RIGHT);
        if (canAdvance) {
            Button next = new Button("Next Question ➔");
            next.getStyleClass().add("primary-button");
            next.setOnAction(e -> {
                next.setDisable(true);
                if (onNextQuestion != null) {
                    onNextQuestion.run();
                }
            });
            footer.getChildren().add(next);
        } else {
            Label waiting = new Label("⏳ Waiting for the host to start the next question...");
            waiting.getStyleClass().add("card-subtitle");
            footer.getChildren().add(waiting);
        }

        getChildren().addAll(title, progressLabel, header, scroll, footer);
    }

    /** Applies a LEADERBOARD payload. Safe to call from any thread. */
    public void update(JsonNode payload) {
        UiCallbackDispatcher.runOnFxThread(() -> {
            if (payload == null) {
                return;
            }
            int closed = payload.path("questionsClosed").asInt(0);
            int total = payload.path("totalQuestions").asInt(0);
            progressLabel.setText("After question " + closed + " of " + total);
            fillRows(rowsBox, payload.path("entries"), myPlayerId);
        });
    }

    /** Fills a container with leaderboard rows, or an empty-state message. Shared with the results screen. */
    static void fillRows(VBox target, JsonNode entries, String myPlayerId) {
        target.getChildren().clear();
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            Label empty = new Label("No players on the scoreboard yet.");
            empty.getStyleClass().add("card-subtitle");
            target.getChildren().add(empty);
            return;
        }
        for (JsonNode e : entries) {
            target.getChildren().add(row(e, myPlayerId));
        }
    }

    static Region headerRow() {
        HBox h = new HBox(12);
        h.setPadding(new Insets(0, 14, 0, 14));
        h.getChildren().addAll(
                fixed(new Label("RANK"), 56),
                grow(new Label("PLAYER")),
                fixed(new Label("CORRECT"), 80),
                fixed(new Label("SCORE"), 80));
        h.getChildren().forEach(n -> n.getStyleClass().add("leaderboard-header"));
        return h;
    }

    static HBox row(JsonNode e, String myPlayerId) {
        int rank = e.path("rank").asInt();
        String pid = e.path("playerId").asText();
        String name = e.path("username").asText(pid);
        boolean connected = e.path("connected").asBoolean(true);
        boolean me = pid.equals(myPlayerId);

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.getStyleClass().add("leaderboard-row");
        if (me) {
            row.getStyleClass().add("leaderboard-row-me");
        }
        if (!connected) {
            row.getStyleClass().add("leaderboard-row-offline");
        }

        String medal = switch (rank) {
            case 1 -> "🥇 ";
            case 2 -> "🥈 ";
            case 3 -> "🥉 ";
            default -> "";
        };
        Label rankLabel = new Label(medal + "#" + rank);
        rankLabel.getStyleClass().add("leaderboard-rank");

        HBox nameBox = new HBox(8);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(name + (me ? "  (you)" : ""));
        nameLabel.getStyleClass().add("leaderboard-name");
        nameBox.getChildren().add(nameLabel);
        if (!connected) {
            Label off = new Label("DISCONNECTED");
            off.getStyleClass().add("offline-badge");
            nameBox.getChildren().add(off);
        }

        Label correct = new Label(String.valueOf(e.path("correctCount").asInt()));
        correct.getStyleClass().add("leaderboard-cell");
        Label score = new Label(e.path("score").asInt() + " pts");
        score.getStyleClass().add("leaderboard-score");

        row.getChildren().addAll(fixed(rankLabel, 56), grow(nameBox), fixed(correct, 80), fixed(score, 80));
        return row;
    }

    private static <T extends Region> T fixed(T node, double width) {
        node.setMinWidth(width);
        node.setPrefWidth(width);
        return node;
    }

    private static <T extends Region> T grow(T node) {
        HBox.setHgrow(node, Priority.ALWAYS);
        node.setMaxWidth(Double.MAX_VALUE);
        return node;
    }
}
