package com.quizbattle.client.ui;

import com.quizbattle.app.AppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

/**
 * Screen 2: Manual TCP Server Connection View.
 * Allows manual configuration of server host IP and TCP port, with real-time error display.
 */
public class ManualConnectView extends VBox {

    private final TextField hostField;
    private final TextField portField;
    private final Label statusLabel;
    private final Label errorLabel;
    private final Button connectButton;
    private final ProgressIndicator progressSpinner;

    @SuppressWarnings("this-escape")
    public ManualConnectView(String username, BiConsumer<String, Integer> onConnect, Runnable onBack) {
        super(16);
        getStyleClass().add("welcome-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(460);
        setPadding(new Insets(32));

        Label heading = new Label("Server Connection");
        heading.getStyleClass().add("card-heading");

        Label playerGreeting = new Label("Connecting as: " + username);
        playerGreeting.getStyleClass().add("player-badge");

        Label hostLabel = new Label("Server Host / IP Address:");
        hostLabel.getStyleClass().add("field-label");

        hostField = new TextField("127.0.0.1");
        hostField.setPromptText("127.0.0.1 or hostname");
        hostField.getStyleClass().add("input-field");

        Label portLabel = new Label("TCP Server Port (default 5000):");
        portLabel.getStyleClass().add("field-label");

        portField = new TextField(String.valueOf(AppConfig.DEFAULT_SERVER_PORT));
        portField.setPromptText("Port");
        portField.getStyleClass().add("input-field");

        // Status & Error indicators
        statusLabel = new Label("Status: Ready to connect");
        statusLabel.getStyleClass().add("status-label");

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-box");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        progressSpinner = new ProgressIndicator();
        progressSpinner.setPrefSize(20, 20);
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);

        HBox statusRow = new HBox(10, progressSpinner, statusLabel);
        statusRow.setAlignment(Pos.CENTER);

        connectButton = new Button("Connect to Quiz Server");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);

        Button backButton = new Button("⬅ Back to Username");
        backButton.getStyleClass().add("secondary-button");
        backButton.setMaxWidth(Double.MAX_VALUE);

        connectButton.setOnAction(e -> {
            String host = hostField.getText().trim();
            if (host.isBlank()) {
                showError("Server Host/IP cannot be blank.");
                return;
            }
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
                if (port < 1 || port > 65535) {
                    showError("Port must be a valid number between 1 and 65535.");
                    return;
                }
            } catch (NumberFormatException ex) {
                showError("Invalid port number: " + portField.getText());
                return;
            }

            clearError();
            setConnecting(true);
            if (onConnect != null) {
                onConnect.accept(host, port);
            }
        });

        backButton.setOnAction(e -> {
            if (onBack != null) {
                onBack.run();
            }
        });

        getChildren().addAll(
                heading,
                playerGreeting,
                hostLabel,
                hostField,
                portLabel,
                portField,
                errorLabel,
                statusRow,
                connectButton,
                backButton
        );
    }

    public void showError(String errorMessage) {
        errorLabel.setText("⚠️ " + errorMessage);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        setConnecting(false);
    }

    public void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public void setConnecting(boolean connecting) {
        progressSpinner.setVisible(connecting);
        progressSpinner.setManaged(connecting);
        connectButton.setDisable(connecting);
        if (connecting) {
            statusLabel.setText("Status: Connecting over TCP...");
        } else {
            statusLabel.setText("Status: Ready to connect");
        }
    }
}
