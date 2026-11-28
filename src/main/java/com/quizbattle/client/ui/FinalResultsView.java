package com.quizbattle.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Member 5: Final Results screen, shown on QUIZ_FINISHED.
 *
 * <p>Shows the winner (or a tie), the player's own rank/score/correct count, and the full final
 * standings. All figures come from the server's QUIZ_FINISHED payload.</p>
 */
public class FinalResultsView extends VBox {

    @SuppressWarnings("this-escape")
    public FinalResultsView(
            String myPlayerId,
            JsonNode payload,
            boolean canPlayAgain,
            Runnable onViewSummary,
            Runnable onPlayAgain,
            Runnable onLeave) {
        super(16);
        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(720);
        setMaxHeight(Region.USE_PREF_SIZE);
        setPadding(new Insets(28, 32, 28, 32));

        Label title = new Label("🏁 Final Results");
        title.getStyleClass().add("card-heading");

        JsonNode standings = payload != null ? payload.path("finalStandings") : null;
        int total = payload != null ? payload.path("totalQuestions").asInt(0) : 0;

        Label winnerBanner = new Label(winnerText(payload, standings));
        winnerBanner.getStyleClass().add("winner-banner");
        winnerBanner.setWrapText(true);
        winnerBanner.setMaxWidth(Double.MAX_VALUE);

        Label personal = new Label(personalText(myPlayerId, standings, total));
        personal.getStyleClass().add("personal-summary-line");
        personal.setWrapText(true);

        VBox rows = new VBox(8);
        LeaderboardView.fillRows(rows, standings, myPlayerId);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(240);
        scroll.getStyleClass().add("leaderboard-scroll");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button summary = new Button("📋 My Summary");
        summary.getStyleClass().add("secondary-button");
        summary.setOnAction(e -> run(onViewSummary));
        actions.getChildren().add(summary);
        if (canPlayAgain) {
            Button again = new Button("🔁 Play Again");
            again.getStyleClass().add("secondary-button");
            again.setOnAction(e -> run(onPlayAgain));
            actions.getChildren().add(again);
        }
        Button leave = new Button("🏠 Leave Room");
        leave.getStyleClass().add("primary-button");
        leave.setOnAction(e -> run(onLeave));
        actions.getChildren().add(leave);

        getChildren().addAll(title, winnerBanner, personal, LeaderboardView.headerRow(), scroll, actions);
    }

    static String winnerText(JsonNode payload, JsonNode standings) {
        List<String> winners = new ArrayList<>();
        if (payload != null && payload.path("winners").isArray()) {
            payload.path("winners").forEach(w -> winners.add(w.asText()));
        }
        if (winners.isEmpty()) {
            return (standings == null || standings.isEmpty())
                    ? "No players finished this quiz."
                    : "No winner: nobody answered correctly.";
        }
        int topScore = standings != null && !standings.isEmpty() ? standings.get(0).path("score").asInt() : 0;
        if (winners.size() == 1) {
            return "🏆 Winner: " + winners.get(0) + " with " + topScore + " pts";
        }
        return "🤝 Tie for first (" + topScore + " pts): " + String.join(", ", winners);
    }

    static String personalText(String myPlayerId, JsonNode standings, int total) {
        JsonNode me = findEntry(standings, myPlayerId);
        if (me == null) {
            return "You did not take part in this quiz.";
        }
        int players = standings.size();
        return "You placed #" + me.path("rank").asInt() + " of " + players
                + "  ·  " + me.path("score").asInt() + " pts"
                + "  ·  " + me.path("correctCount").asInt() + "/" + total + " correct";
    }

    static JsonNode findEntry(JsonNode standings, String playerId) {
        if (standings == null || !standings.isArray() || playerId == null) {
            return null;
        }
        for (JsonNode e : standings) {
            if (playerId.equals(e.path("playerId").asText())) {
                return e;
            }
        }
        return null;
    }

    private static void run(Runnable r) {
        if (r != null) {
            r.run();
        }
    }
}
