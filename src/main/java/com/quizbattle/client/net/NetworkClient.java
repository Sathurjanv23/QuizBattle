package com.quizbattle.client.net;

import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Network client contract for client-side transport communication.
 *
 * <p>Framing contract:
 * All messages sent and received are newline-delimited UTF-8 JSON.
 * Incoming messages must trigger listeners. When updating JavaFX UI controls,
 * listeners MUST dispatch through {@code Platform.runLater()}.</p>
 */
public interface NetworkClient extends AutoCloseable {

    /**
     * Connects to the QuizBattle TCP server.
     *
     * @param host server hostname or IP address
     * @param port server TCP port (default 5000)
     * @throws IOException if socket connection cannot be established
     */
    void connect(String host, int port) throws IOException;

    /**
     * Closes the active network connection and releases I/O threads.
     */
    void disconnect();

    /**
     * Returns true if client socket is currently open and active.
     */
    boolean isConnected();

    /**
     * Current state of network connection.
     */
    ClientConnectionState getConnectionState();

    /**
     * Enqueues a message to be framed with trailing newline and sent to server.
     *
     * @param message protocol envelope
     */
    void send(Message message);

    /**
     * Registers a listener for a specific message type.
     */
    void registerListener(MessageType type, Consumer<Message> listener);

    /**
     * Registers a catch-all listener for all inbound messages.
     */
    void registerGlobalListener(Consumer<Message> listener);

    /**
     * Registers a callback for connection state transitions.
     */
    void onConnectionStateChange(Consumer<ClientConnectionState> stateConsumer);
}
