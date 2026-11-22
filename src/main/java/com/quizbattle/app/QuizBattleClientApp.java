package com.quizbattle.app;

import com.quizbattle.client.ui.NavigationContainer;
import com.quizbattle.client.ui.WelcomeView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * JavaFX Client Application entry point.
 *
 * <p>Execution Command:
 * <pre>
 * mvn javafx:run
 * </pre>
 * or
 * <pre>
 * mvn compile exec:java -Dexec.mainClass="com.quizbattle.client.ClientLauncher"
 * </pre>
 * </p>
 */
public class QuizBattleClientApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(QuizBattleClientApp.class);

    @Override
    public void start(Stage primaryStage) {
        log.info("Initializing QuizBattle 2.0 JavaFX Client UI");

        NavigationContainer rootContainer = new NavigationContainer();

        WelcomeView welcomeView = new WelcomeView((host, port) -> {
            log.info("User requested connection to {}:{}", host, port);
            rootContainer.setConnectionStatus("Connecting...", "#3b82f6");
        });

        rootContainer.navigateTo(welcomeView);

        Scene scene = new Scene(rootContainer, 860, 600);

        URL cssResource = getClass().getResource("/styles/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            log.warn("Theme stylesheet /styles/app.css not found on classpath");
        }

        primaryStage.setTitle("QuizBattle 2.0");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(640);
        primaryStage.setMinHeight(480);
        primaryStage.show();

        log.info("QuizBattle Client UI displayed successfully.");
    }
}
