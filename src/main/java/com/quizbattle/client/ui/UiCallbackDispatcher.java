package com.quizbattle.client.ui;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility dispatcher ensuring all network, background, and timer callbacks
 * safely execute on the JavaFX Application Thread.
 */
public final class UiCallbackDispatcher {

    private static final Logger log = LoggerFactory.getLogger(UiCallbackDispatcher.class);

    private UiCallbackDispatcher() {
    }

    /**
     * Executes the given action on the JavaFX Application Thread.
     * If already running on the JavaFX thread, executes immediately.
     */
    public static void runOnFxThread(Runnable action) {
        if (action == null) return;
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (Throwable t) {
            // JavaFX toolkit might not be initialized during headless tests
            log.debug("JavaFX platform not initialized, executing action in caller thread: {}", t.getMessage());
            action.run();
        }
    }
}
