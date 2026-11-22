package com.quizbattle.client;

import com.quizbattle.app.QuizBattleClientApp;
import javafx.application.Application;

/**
 * Main application launcher for the JavaFX client.
 *
 * <p>Technical Note:
 * Starting via this standalone launcher class (which does not extend {@link Application})
 * allows the JavaFX runtime to be launched seamlessly under modular or non-modular Maven execution
 * on both Windows and Linux without requiring runtime VM module-path arguments.</p>
 */
public class ClientLauncher {

    public static void main(String[] args) {
        Application.launch(QuizBattleClientApp.class, args);
    }
}
