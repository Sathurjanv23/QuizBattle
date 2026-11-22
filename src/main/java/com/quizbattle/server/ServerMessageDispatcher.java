package com.quizbattle.server;

import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;

import java.util.function.BiConsumer;

/**
 * Transport-neutral server message dispatch contract.
 *
 * <p>Decouples network socket handling (blocking I/O or NIO) from message routing
 * and business domain processing.</p>
 */
public interface ServerMessageDispatcher {

    /**
     * Routes an inbound message from a client session to the registered handler.
     *
     * @param session the client session that delivered the message
     * @param message the decoded protocol envelope
     */
    void dispatch(ClientSession session, Message message);

    /**
     * Registers a consumer for a specific message type.
     */
    void registerHandler(MessageType type, BiConsumer<ClientSession, Message> handler);

    /**
     * Called when a new client session connects to the server.
     */
    void onSessionConnected(ClientSession session);

    /**
     * Called when a client session disconnects from the server.
     */
    void onSessionDisconnected(ClientSession session);
}
