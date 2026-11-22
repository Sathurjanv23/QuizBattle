package com.quizbattle.server;

import com.quizbattle.protocol.Message;

/**
 * Server-side abstraction of a connected client session.
 *
 * <p>Central Security Invariant:
 * The server associates the client socket/channel with this {@code ClientSession}.
 * Player identity ({@code playerId}), username, and room context are bound here on authentication.
 * Inbound messages MUST be verified against this session; the client cannot self-assign
 * scores, roles, or unauthorized player identifiers.</p>
 */
public interface ClientSession {

    /**
     * Unique identifier of the network session.
     */
    String getSessionId();

    /**
     * The authenticated player ID, or {@code null} if not yet authenticated.
     */
    String getPlayerId();

    void setPlayerId(String playerId);

    String getUsername();

    void setUsername(String username);

    String getCurrentRoomId();

    void setCurrentRoomId(String roomId);

    boolean isAuthenticated();

    /**
     * Sends a protocol message to this client.
     */
    void send(Message message);

    /**
     * Terminates the client connection and cleans up associated state.
     */
    void close();

    boolean isConnected();
}
