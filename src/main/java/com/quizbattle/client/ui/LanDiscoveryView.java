package com.quizbattle.client.ui;

import com.quizbattle.app.AppConfig;
import com.quizbattle.discovery.DiscoveredServer;
import com.quizbattle.discovery.UdpDiscoveryClient;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 LAN Discovery screen.
 Scans the LAN over UDP, lists the servers that answered and connects over TCP.
 The scan runs on a background thread so the JavaFX UI never freezes.
 */
public class LanDiscoveryView extends VBox {

    private final UdpDiscoveryClient discoveryClient;
    private final BiConsumer<String, Integer> onConnect;

    private final ListView<DiscoveredServer> serverList;
    private final Label statusLabel;
    private final Label errorLabel;
    private final ProgressIndicator progressSpinner;
    private final Button scanButton;
    private final Button connectButton;

    private volatile boolean scanning;

    @SuppressWarnings("this-escape")
    public LanDiscoveryView(String username,
                            UdpDiscoveryClient discoveryClient,
                            BiConsumer<String, Integer> onConnect,
                            Runnable onManualConnect,
                            Runnable onBack) {
        super(14);
        this.discoveryClient = discoveryClient;
        this.onConnect = onConnect;

        getStyleClass().add("welcome-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(560);
        setPadding(new Insets(28));

        // ---------- Labels ----------
        Label heading = new Label("Find a Quiz Server");
        heading.getStyleClass().add("card-heading");

        Label playerGreeting = new Label("Connecting as: " + username);
        playerGreeting.getStyleClass().add("player-badge");

        Label subtitle = new Label("Scans your local network with a UDP broadcast on port "
                + AppConfig.getDiscoveryPort() + ". Gameplay then uses TCP.");
        subtitle.getStyleClass().add("card-subtitle");
        subtitle.setWrapText(true);

        // ---------- Server list ----------
        serverList = new ListView<>();
        serverList.setPrefHeight(170);
        serverList.setPlaceholder(new Label("No servers yet. Click \"Scan LAN\"."));
        serverList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> connectButton().setDisable(newValue == null || scanning));
        serverList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                connectToSelected(); // double-click = connect
            }
        });

        // ---------- Status / error ----------
        statusLabel = new Label("Status: Ready to scan");
        statusLabel.getStyleClass().add("status-label");

        progressSpinner = new ProgressIndicator();
        progressSpinner.setPrefSize(20, 20);
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);

        HBox statusRow = new HBox(10, progressSpinner, statusLabel);
        statusRow.setAlignment(Pos.CENTER);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-box");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // ---------- Buttons ----------
        scanButton = new Button("🔍 Scan LAN");
        scanButton.getStyleClass().add("primary-button");
        scanButton.setMaxWidth(Double.MAX_VALUE);
        scanButton.setOnAction(e -> scan());

        connectButton = new Button("Connect to Selected Server");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setDisable(true);
        connectButton.setOnAction(e -> connectToSelected());

        Button manualButton = new Button("⌨ Enter IP Manually");
        manualButton.getStyleClass().add("secondary-button");
        manualButton.setMaxWidth(Double.MAX_VALUE);
        manualButton.setOnAction(e -> {
            if (onManualConnect != null) {
                onManualConnect.run();
            }
        });

        Button backButton = new Button("⬅ Back to Username");
        backButton.getStyleClass().add("secondary-button");
        backButton.setMaxWidth(Double.MAX_VALUE);
        backButton.setOnAction(e -> {
            if (onBack != null) {
                onBack.run();
            }
        });

        getChildren().addAll(heading, playerGreeting, subtitle, serverList,
                errorLabel, statusRow, scanButton, connectButton, manualButton, backButton);

        scan(); // scan automatically when the screen opens
    }

    private Button connectButton() {
        return connectButton;
    }

    /** Runs the UDP scan on a background thread, then shows results on the JavaFX thread. */
    public void scan() {
        if (scanning) {
            return;
        }
        setScanning(true);
        clearError();
        serverList.getItems().clear();

        CompletableFuture
                .supplyAsync(() -> discoveryClient.discover(UdpDiscoveryClient.DEFAULT_TIMEOUT)) // background
                .whenComplete((servers, error) -> UiCallbackDispatcher.runOnFxThread(() -> {     // back to UI
                    setScanning(false);
                    if (error != null) {
                        showError("Scan failed: " + error.getMessage());
                        return;
                    }
                    showResults(servers);
                }));
    }

    private void showResults(List<DiscoveredServer> servers) {
        serverList.getItems().setAll(servers);
        if (servers.isEmpty()) {
            statusLabel.setText("Status: No servers found");
            showError("No QuizBattle server answered. Make sure the server is running. "
                    + "If this network blocks UDP broadcast, use \"Enter IP Manually\".");
        } else {
            statusLabel.setText("Status: Found " + servers.size() + " server(s). Select one and connect.");
            serverList.getSelectionModel().selectFirst();
        }
    }

    private void connectToSelected() {
        DiscoveredServer selected = serverList.getSelectionModel().getSelectedItem();
        if (selected == null || scanning) {
            return;
        }
        clearError();
        setConnecting(true, selected);
        if (onConnect != null) {
            onConnect.accept(selected.host(), selected.tcpPort()); // TCP connect (Member 1's code)
        }
    }

    private void setScanning(boolean value) {
        scanning = value;
        progressSpinner.setVisible(value);
        progressSpinner.setManaged(value);
        scanButton.setDisable(value);
        connectButton.setDisable(value || serverList.getSelectionModel().getSelectedItem() == null);
        if (value) {
            statusLabel.setText("Status: Broadcasting UDP discovery request...");
        }
    }

    private void setConnecting(boolean connecting, DiscoveredServer server) {
        progressSpinner.setVisible(connecting);
        progressSpinner.setManaged(connecting);
        connectButton.setDisable(connecting);
        scanButton.setDisable(connecting);
        if (connecting && server != null) {
            statusLabel.setText("Status: Connecting over TCP to " + server.host() + ":" + server.tcpPort() + "...");
        }
    }

    /** Called by the app when the TCP connection fails. */
    public void showError(String errorMessage) {
        errorLabel.setText("⚠️ " + errorMessage);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        setConnecting(false, null);
        if (!scanning) {
            statusLabel.setText("Status: Ready to scan");
        }
    }

    public void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}