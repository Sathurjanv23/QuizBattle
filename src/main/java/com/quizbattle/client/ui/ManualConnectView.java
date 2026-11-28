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
 * Screen 3: Manual TCP Server Connection View.
 * Allows manual configuration of server host IP and TCP port, with human-friendly error feedback.
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
        super(18);
        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(480);
        setPadding(new Insets(32));

        // 1. Heading & Identity
        Label heading = new Label("Direct Server Connection");
        heading.getStyleClass().add("card-heading");

        Label playerGreeting = new Label("👤 Player: " + username);
        playerGreeting.getStyleClass().add("player-badge");

        Label subtitle = new Label("Enter the TCP IP address and port of your QuizBattle host server.");
        subtitle.getStyleClass().add("card-subtitle");
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        subtitle.setWrapText(true);

        VBox headerBox = new VBox(6, heading, playerGreeting, subtitle);
        headerBox.setAlignment(Pos.CENTER);

        // 2. Form Inputs
        VBox formBox = new VBox(12);
        formBox.setAlignment(Pos.CENTER_LEFT);

        Label hostLabel = new Label("SERVER HOST / IP ADDRESS");
        hostLabel.getStyleClass().add("field-label");

        hostField = new TextField("127.0.0.1");
        hostField.setPromptText("127.0.0.1 or domain");
        hostField.getStyleClass().add("input-field");

        Label portLabel = new Label("TCP SERVER PORT (DEFAULT 5000)");
        portLabel.getStyleClass().add("field-label");

        portField = new TextField(String.valueOf(AppConfig.DEFAULT_SERVER_PORT));
        portField.setPromptText("5000");
        portField.getStyleClass().add("input-field");

        formBox.getChildren().addAll(hostLabel, hostField, portLabel, portField);

        // 3. Status & Error indicators
        statusLabel = new Label("Status: Ready to connect");
        statusLabel.getStyleClass().add("status-label");

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-box");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        progressSpinner = new ProgressIndicator();
        progressSpinner.setPrefSize(18, 18);
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);

        HBox statusRow = new HBox(8, progressSpinner, statusLabel);
        statusRow.setAlignment(Pos.CENTER);

        // 4. Action Buttons
        connectButton = new Button("Connect to Quiz Server ➔");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setStyle("-fx-font-size: 15px; -fx-padding: 13px 24px;");

        Button backButton = new Button("⬅ Back to Discovery");
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

        hostField.setOnAction(e -> connectButton.fire());
        portField.setOnAction(e -> connectButton.fire());

        backButton.setOnAction(e -> {
            if (onBack != null) {
                onBack.run();
            }
        });

        getChildren().addAll(headerBox, formBox, errorLabel, statusRow, connectButton, backButton);
    }

    public void setConnecting(boolean connecting) {
        progressSpinner.setVisible(connecting);
        progressSpinner.setManaged(connecting);
        connectButton.setDisable(connecting);
        hostField.setDisable(connecting);
        portField.setDisable(connecting);
        if (connecting) {
            statusLabel.setText("Connecting to " + hostField.getText() + ":" + portField.getText() + "...");
        } else {
            statusLabel.setText("Status: Ready to connect");
        }
    }

    /** Formats raw network exceptions into clean, human-readable guidance. */
    public void showError(String rawMessage) {
        String friendly;
        if (rawMessage == null || rawMessage.isBlank()) {
            friendly = "Unable to connect to the server. Please verify the host and port.";
        } else if (rawMessage.contains("Connection refused") || rawMessage.contains("ConnectException")) {
            friendly = "Unable to connect. The QuizBattle server is not running on "
                    + hostField.getText().trim() + ":" + portField.getText().trim()
                    + ".\nPlease start the server first or check the port number.";
        } else if (rawMessage.contains("UnknownHostException")) {
            friendly = "Hostname '" + hostField.getText().trim() + "' could not be resolved. Please verify the IP address.";
        } else if (rawMessage.contains("SocketTimeoutException")) {
            friendly = "Connection timed out. The server did not respond in time.";
        } else {
            friendly = rawMessage;
        }

        errorLabel.setText("⚠ " + friendly);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        setConnecting(false);
    }

    public void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
    }
}
