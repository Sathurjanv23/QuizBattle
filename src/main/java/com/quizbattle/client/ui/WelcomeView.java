package com.quizbattle.client.ui;

import com.quizbattle.app.AppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

/**
 * Initial Welcome placeholder view presenting player identity input,
 * server host/port configuration, and navigation entry points.
 */
public class WelcomeView extends VBox {

    private final TextField usernameField;
    private final TextField hostField;
    private final TextField portField;
    private final Button connectButton;
    private final Label infoLabel;

    @SuppressWarnings("this-escape")
    public WelcomeView(BiConsumer<String, Integer> onConnectAction) {
        super(18);
        getStyleClass().add("welcome-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(460);
        setPadding(new Insets(32));

        Label heading = new Label("Welcome to QuizBattle");
        heading.getStyleClass().add("card-heading");

        Label subtitle = new Label("Concurrent Network Quiz Arena");
        subtitle.getStyleClass().add("card-subtitle");

        // Input Fields
        usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.getStyleClass().add("input-field");

        hostField = new TextField("127.0.0.1");
        hostField.setPromptText("Server Host / IP");
        hostField.getStyleClass().add("input-field");

        portField = new TextField(String.valueOf(AppConfig.DEFAULT_SERVER_PORT));
        portField.setPromptText("Port");
        portField.getStyleClass().add("input-field");
        portField.setPrefWidth(90);

        HBox connectionRow = new HBox(10, hostField, portField);
        connectionRow.setAlignment(Pos.CENTER);

        connectButton = new Button("Join Arena");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);

        infoLabel = new Label("TCP Port: " + AppConfig.DEFAULT_SERVER_PORT +
                " | Discovery UDP: " + AppConfig.DEFAULT_DISCOVERY_PORT);
        infoLabel.getStyleClass().add("info-label");

        connectButton.setOnAction(e -> {
            String host = hostField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                port = AppConfig.DEFAULT_SERVER_PORT;
            }
            if (onConnectAction != null) {
                onConnectAction.accept(host, port);
            }
        });

        getChildren().addAll(heading, subtitle, usernameField, connectionRow, connectButton, infoLabel);
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getHost() {
        return hostField.getText().trim();
    }

    public int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return AppConfig.DEFAULT_SERVER_PORT;
        }
    }
}
