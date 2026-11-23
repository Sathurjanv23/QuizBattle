package com.quizbattle.client.net;

import com.quizbattle.client.ui.UiCallbackDispatcher;
import com.quizbattle.protocol.JsonMessageCodec;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageCodec;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.protocol.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Robust TCP socket client implementation using {@link Socket}.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Asynchronous daemon background reader thread preventing JavaFX thread blocking.</li>
 *   <li>Line-based newline-delimited framing handled cleanly.</li>
 *   <li>Graceful handling of server EOF, unexpected disconnects, and malformed messages.</li>
 *   <li>All listener callbacks route to the JavaFX Application Thread via {@link UiCallbackDispatcher}.</li>
 * </ul>
 * </p>
 */
public class SocketNetworkClient implements NetworkClient {

    private static final Logger log = LoggerFactory.getLogger(SocketNetworkClient.class);
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final MessageCodec codec;
    private final Map<MessageType, List<Consumer<Message>>> typeListeners = new ConcurrentHashMap<>();
    private final List<Consumer<Message>> globalListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ClientConnectionState>> stateListeners = new CopyOnWriteArrayList<>();

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread readerThread;

    private volatile ClientConnectionState connectionState = ClientConnectionState.DISCONNECTED;
    private final Object writeLock = new Object();

    public SocketNetworkClient() {
        this(new JsonMessageCodec());
    }

    public SocketNetworkClient(MessageCodec codec) {
        this.codec = codec != null ? codec : new JsonMessageCodec();
    }

    @Override
    public synchronized void connect(String host, int port) throws IOException {
        if (isConnected()) {
            log.warn("SocketNetworkClient is already connected. Disconnecting prior session first.");
            disconnect();
        }

        updateState(ClientConnectionState.CONNECTING);
        log.info("Connecting to TCP server at {}:{}...", host, port);

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            updateState(ClientConnectionState.CONNECTED);
            log.info("Successfully connected to {}:{}", host, port);

            // Start background reader thread
            readerThread = new Thread(this::readLoop, "quizbattle-client-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (IOException e) {
            updateState(ClientConnectionState.ERROR);
            cleanupResources();
            throw e;
        }
    }

    private void readLoop() {
        log.debug("Client reading loop started");
        BufferedReader localReader = reader;
        if (localReader == null) {
            log.debug("Client reader unavailable; terminating read loop");
            return;
        }
        try {
            String line;
            while ((line = localReader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Message message = codec.decode(line);
                    dispatchMessage(message);
                } catch (ProtocolException e) {
                    log.error("Failed to decode message from server: {}. Raw line: '{}'", e.getMessage(), line);
                    // Do not break reader loop on single malformed message
                }
            }
            log.info("Server closed connection (EOF received)");
        } catch (IOException e) {
            if (connectionState != ClientConnectionState.DISCONNECTED) {
                log.warn("Socket read exception: {}", e.getMessage());
            }
        } finally {
            log.debug("Client reading loop terminated");
            disconnect();
        }
    }

    private void dispatchMessage(Message message) {
        log.debug("Received message: type={}, requestId={}", message.getType(), message.getRequestId());

        // Dispatch to type-specific listeners on FX thread
        List<Consumer<Message>> listeners = typeListeners.get(message.getType());
        if (listeners != null) {
            for (Consumer<Message> listener : listeners) {
                UiCallbackDispatcher.runOnFxThread(() -> {
                    try {
                        listener.accept(message);
                    } catch (Exception ex) {
                        log.error("Error in message listener for {}: {}", message.getType(), ex.getMessage(), ex);
                    }
                });
            }
        }

        // Dispatch to global listeners on FX thread
        for (Consumer<Message> listener : globalListeners) {
            UiCallbackDispatcher.runOnFxThread(() -> {
                try {
                    listener.accept(message);
                } catch (Exception ex) {
                    log.error("Error in global message listener: {}", ex.getMessage(), ex);
                }
            });
        }
    }

    @Override
    public void send(Message message) {
        if (!isConnected()) {
            log.error("Cannot send message: Client is not connected (state: {})", connectionState);
            return;
        }
        try {
            String wireString = codec.encodeToString(message);
            synchronized (writeLock) {
                if (writer != null) {
                    writer.write(wireString);
                    writer.flush();
                    log.debug("Sent message: type={}, requestId={}", message.getType(), message.getRequestId());
                }
            }
        } catch (ProtocolException | IOException e) {
            log.error("Failed to send message {}: {}", message.getType(), e.getMessage());
            updateState(ClientConnectionState.ERROR);
            disconnect();
        }
    }

    @Override
    public synchronized void disconnect() {
        if (connectionState == ClientConnectionState.DISCONNECTED) {
            return;
        }
        log.info("Disconnecting client socket...");
        cleanupResources();
        updateState(ClientConnectionState.DISCONNECTED);
    }

    private synchronized void cleanupResources() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        reader = null;
        writer = null;
        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
            readerThread = null;
        }
    }

    private void updateState(ClientConnectionState newState) {
        if (this.connectionState != newState) {
            this.connectionState = newState;
            log.info("Client connection state changed to: {}", newState);
            for (Consumer<ClientConnectionState> listener : stateListeners) {
                UiCallbackDispatcher.runOnFxThread(() -> {
                    try {
                        listener.accept(newState);
                    } catch (Exception ex) {
                        log.error("Error in connection state listener: {}", ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed() &&
                connectionState == ClientConnectionState.CONNECTED;
    }

    @Override
    public ClientConnectionState getConnectionState() {
        return connectionState;
    }

    @Override
    public void registerListener(MessageType type, Consumer<Message> listener) {
        typeListeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void registerGlobalListener(Consumer<Message> listener) {
        globalListeners.add(listener);
    }

    @Override
    public void onConnectionStateChange(Consumer<ClientConnectionState> stateConsumer) {
        stateListeners.add(stateConsumer);
    }

    @Override
    public void close() {
        disconnect();
    }
}
