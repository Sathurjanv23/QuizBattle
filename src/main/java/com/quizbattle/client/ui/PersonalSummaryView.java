package com.quizbattle.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Map;

/**
 * Member 5: Personal Summary screen.
 *
 * <p>Rank, score, correct/total and accuracy come from the server's final standings. The
 * per-question list replays the server's ANSWER_RESULT verdicts this client received; it is
 * display-only and never feeds back into scoring.</p>
 */
public class PersonalSummaryView extends VBox {

    @SuppressWarnings("this-escape")
    public PersonalSummaryView(
            String username,
            String myPlayerId,
            JsonNode finishedPayload,
            Map<String, String> answerHistory,
            Runnable onBackToResults,
            Runnable onLeave) {
        super(16);
        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(640);
        setMaxHeight(Region.USE_PREF_SIZE);
        setPadding(new Insets(28, 32, 28, 32));

        Label title = new Label("📋 Personal Summary — " + username);
        title.getStyleClass().add("card-heading");

        JsonNode standings = finishedPayload != null ? finishedPayload.path("finalStandings") : null;
        int total = finishedPayload != null ? finishedPayload.path("totalQuestions").asInt(0) : 0;
        JsonNode me = FinalResultsView.findEntry(standings, myPlayerId);

        getChildren().addAll(title);

        if (me == null) {
            Label none = new Label("No score recorded for you in this quiz.");
            none.getStyleClass().add("card-subtitle");
            getChildren().add(none);
        } else {
            int score = me.path("score").asInt();
            int correct = me.path("correctCount").asInt();
            int accuracy = total > 0 ? Math.round(100f * correct / total) : 0;

            GridPane stats = new GridPane();
            stats.setHgap(24);
            stats.setVgap(8);
            addStat(stats, 0, "Final rank", "#" + me.path("rank").asInt() + " of " + standings.size());
            addStat(stats, 1, "Score", score + " pts");
            addStat(stats, 2, "Correct answers", correct + " / " + total);
            addStat(stats, 3, "Accuracy", accuracy + "%");
            getChildren().add(stats);
        }

        Label historyTitle = new Label("Question by question");
        historyTitle.getStyleClass().add("field-label");
        VBox history = new VBox(6);
        if (answerHistory == null || answerHistory.isEmpty()) {
            Label empty = new Label("No answers recorded on this device.");
            empty.getStyleClass().add("card-subtitle");
            history.getChildren().add(empty);
        } else {
            int n = 1;
            for (Map.Entry<String, String> e : answerHistory.entrySet()) {
                Label line = new Label("Q" + n++ + "  (" + e.getKey() + ")   " + e.getValue());
                line.getStyleClass().add("summary-history-line");
                history.getChildren().add(line);
            }
        }
        getChildren().addAll(historyTitle, history);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button back = new Button("← Back to Results");
        back.getStyleClass().add("secondary-button");
        back.setOnAction(e -> {
            if (onBackToResults != null) onBackToResults.run();
        });
        Button leave = new Button("🏠 Leave Room");
        leave.getStyleClass().add("primary-button");
        leave.setOnAction(e -> {
            if (onLeave != null) onLeave.run();
        });
        actions.getChildren().addAll(back, leave);
        getChildren().add(actions);
    }

    private static void addStat(GridPane grid, int row, String name, String value) {
        Label k = new Label(name);
        k.getStyleClass().add("card-subtitle");
        Label v = new Label(value);
        v.getStyleClass().add("summary-stat-value");
        grid.add(k, 0, row);
        grid.add(v, 1, row);
    }
}
