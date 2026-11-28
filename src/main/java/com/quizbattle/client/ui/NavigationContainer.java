package com.quizbattle.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Reusable root navigation container managing scene transitions,
 * shared header bar, status indicators, and view history.
 */
public class NavigationContainer extends BorderPane {

    private final StackPane contentArea;
    private final Label titleLabel;
    private final Label statusLabel;
    private final Circle statusIndicator;
    private final Deque<Node> backStack = new ArrayDeque<>();

    @SuppressWarnings("this-escape")
    public NavigationContainer() {
        getStyleClass().add("root-container");

        // Top App Bar
        HBox topBar = new HBox(12);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));

        titleLabel = new Label("⚔️ QuizBattle 2.0");
        titleLabel.getStyleClass().add("app-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusIndicator = new Circle(5, Color.web("#f59e0b")); // amber: offline/idle
        statusLabel = new Label("Disconnected");
        statusLabel.getStyleClass().add("status-label");

        HBox statusBox = new HBox(8, statusIndicator, statusLabel);
        statusBox.setAlignment(Pos.CENTER_RIGHT);

        topBar.getChildren().addAll(titleLabel, spacer, statusBox);
        setTop(topBar);

        // Center Content Area
        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        contentArea.setPadding(new Insets(24));
        setCenter(contentArea);
    }

    /**
     * Navigates to a new view, saving current view to back stack.
     */
    public void navigateTo(Node view) {
        UiCallbackDispatcher.runOnFxThread(() -> {
            if (!contentArea.getChildren().isEmpty()) {
                backStack.push(contentArea.getChildren().getFirst());
            }
            contentArea.getChildren().setAll(view);
        });
    }

    /**
     * Navigates back to the previous view in the stack.
     */
    public boolean navigateBack() {
        if (!backStack.isEmpty()) {
            UiCallbackDispatcher.runOnFxThread(() -> {
                Node prev = backStack.pop();
                contentArea.getChildren().setAll(prev);
            });
            return true;
        }
        return false;
    }

    /**
     * Updates the status indicator in the top header.
     */
    public void setConnectionStatus(String statusText, String colorHex) {
        UiCallbackDispatcher.runOnFxThread(() -> {
            statusLabel.setText(statusText);
            statusIndicator.setFill(Color.web(colorHex));
        });
    }

    public StackPane getContentArea() {
        return contentArea;
    }
}
