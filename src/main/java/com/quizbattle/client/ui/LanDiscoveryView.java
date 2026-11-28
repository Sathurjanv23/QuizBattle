package com.quizbattle.client.ui;

import com.quizbattle.app.AppConfig;
import com.quizbattle.discovery.DiscoveredServer;
import com.quizbattle.discovery.UdpDiscoveryClient;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Screen 2: LAN Server Discovery View.
 * Scans the local network via UDP broadcast (port 5001), renders discovered QuizBattle servers
 * in a sleek dark game browser, and establishes a TCP connection on selection.
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
        super(18);
        this.discoveryClient = discoveryClient;
        this.onConnect = onConnect;

        getStyleClass().addAll("welcome-card", "hero-card");
        setAlignment(Pos.CENTER);
        setMaxWidth(620);
        setPadding(new Insets(32));

        // 1. Heading & Identity
        Label heading = new Label("Find a Quiz Server");
        heading.getStyleClass().add("card-heading");

        Label playerGreeting = new Label("👤 Player: " + username);
        playerGreeting.getStyleClass().add("player-badge");

        Label subtitle = new Label("Broadcasting UDP probe on port " + AppConfig.getDiscoveryPort()
                + " to auto-detect LAN servers. Gameplay then uses high-speed TCP.");
        subtitle.getStyleClass().add("card-subtitle");
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        subtitle.setWrapText(true);

        VBox headerBox = new VBox(8, heading, playerGreeting, subtitle);
        headerBox.setAlignment(Pos.CENTER);

        // 2. Server List with Custom Dark Cell Factory
        serverList = new ListView<>();
        serverList.setPrefHeight(200);
        serverList.getStyleClass().add("list-view");

        Label emptyPlaceholder = new Label("📡 No servers found yet.\nClick \"Scan Network\" or connect manually below.");
        emptyPlaceholder.getStyleClass().add("card-subtitle");
        emptyPlaceholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        serverList.setPlaceholder(emptyPlaceholder);

        serverList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DiscoveredServer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(4, 6, 4, 6));

                    Label serverIcon = new Label("🖥️");
                    serverIcon.setStyle("-fx-font-size: 18px;");

                    VBox infoBox = new VBox(3);
                    Label nameLabel = new Label(item.serverName());
                    nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

                    Label detailLabel = new Label(item.host() + ":" + item.tcpPort()
                            + " • " + item.openRooms() + " room(s) • " + item.onlinePlayers() + " player(s)");
                    detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");
                    infoBox.getChildren().addAll(nameLabel, detailLabel);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Label pingBadge = new Label("⚡ " + item.responseTimeMs() + " ms");
                    pingBadge.getStyleClass().add("pill-badge");

                    row.getChildren().addAll(serverIcon, infoBox, spacer, pingBadge);
                    setGraphic(row);
                    setText(null);
                }
            }
        });

        connectButton = new Button("Connect to Server ➔");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setDisable(true);
        connectButton.setOnAction(e -> connectToSelected());

        serverList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> connectButton.setDisable(newValue == null || scanning));

        serverList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                connectToSelected();
            }
        });

        // 3. Status & Error Feedback
        statusLabel = new Label("Status: Ready to scan network");
        statusLabel.getStyleClass().add("status-label");

        progressSpinner = new ProgressIndicator();
        progressSpinner.setPrefSize(18, 18);
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);

        HBox statusRow = new HBox(10, progressSpinner, statusLabel);
        statusRow.setAlignment(Pos.CENTER);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-box");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // 4. Action Buttons
        HBox primaryActionRow = new HBox(12);
        primaryActionRow.setAlignment(Pos.CENTER);

        scanButton = new Button("🔄 Scan Network");
        scanButton.getStyleClass().add("secondary-button");
        scanButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(scanButton, Priority.ALWAYS);
        scanButton.setOnAction(e -> scan());

        HBox.setHgrow(connectButton, Priority.ALWAYS);
        primaryActionRow.getChildren().addAll(scanButton, connectButton);

        HBox navRow = new HBox(12);
        navRow.setAlignment(Pos.CENTER);

        Button manualButton = new Button("⌨ Connect Manually");
        manualButton.getStyleClass().add("secondary-button");
        manualButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(manualButton, Priority.ALWAYS);
        manualButton.setOnAction(e -> {
            if (onManualConnect != null) {
                onManualConnect.run();
            }
        });

        Button backButton = new Button("⬅ Back");
        backButton.getStyleClass().add("secondary-button");
        backButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(backButton, Priority.ALWAYS);
        backButton.setOnAction(e -> {
            if (onBack != null) {
                onBack.run();
            }
        });

        navRow.getChildren().addAll(manualButton, backButton);

        getChildren().addAll(headerBox, serverList, errorLabel, statusRow, primaryActionRow, navRow);

        scan(); // auto-scan on view entry
    }

    /** Runs the UDP scan on a background thread, then updates the JavaFX thread safely. */
    public void scan() {
        if (scanning) {
            return;
        }
        setScanning(true);
        clearError();
        serverList.getItems().clear();

        CompletableFuture
                .supplyAsync(() -> discoveryClient.discover(UdpDiscoveryClient.DEFAULT_TIMEOUT))
                .whenComplete((servers, error) -> UiCallbackDispatcher.runOnFxThread(() -> {
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
            statusLabel.setText("Status: No servers found on LAN");
            showError("No QuizBattle server responded to UDP broadcast.\n"
                    + "Please ensure the server is running, or click \"Connect Manually\" to enter 127.0.0.1:5000.");
        } else {
            statusLabel.setText("Status: Found " + servers.size() + " online server(s). Select one to connect.");
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
            onConnect.accept(selected.host(), selected.tcpPort());
        }
    }

    private void setScanning(boolean active) {
        this.scanning = active;
        progressSpinner.setVisible(active);
        progressSpinner.setManaged(active);
        scanButton.setDisable(active);
        if (active) {
            statusLabel.setText("Broadcasting UDP discovery probe on port " + AppConfig.getDiscoveryPort() + "...");
            connectButton.setDisable(true);
        }
    }

    private void setConnecting(boolean active, DiscoveredServer server) {
        progressSpinner.setVisible(active);
        progressSpinner.setManaged(active);
        connectButton.setDisable(active);
        scanButton.setDisable(active);
        if (active) {
            statusLabel.setText("Connecting via TCP to " + server.serverName() + " (" + server.host() + ":" + server.tcpPort() + ")...");
        }
    }

    public void showError(String message) {
        errorLabel.setText("⚠ " + message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        statusLabel.setText("Status: Connection error");
        progressSpinner.setVisible(false);
        progressSpinner.setManaged(false);
        connectButton.setDisable(serverList.getSelectionModel().getSelectedItem() == null);
        scanButton.setDisable(false);
    }

    public void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
    }
}
