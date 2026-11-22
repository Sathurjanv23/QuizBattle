package com.quizbattle.server.blocking;

import com.quizbattle.protocol.JsonMessageCodec;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageCodec;
import com.quizbattle.protocol.ProtocolException;
import com.quizbattle.server.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Blocking I/O implementation of {@link ClientSession} wrapping a standard TCP {@link Socket}.
 */
public class BlockingClientSession implements ClientSession {

    private static final Logger log = LoggerFactory.getLogger(BlockingClientSession.class);

    private final String sessionId;
    private final Socket socket;
    private final MessageCodec codec;
    private final BufferedWriter writer;

    private volatile String playerId;
    private volatile String username;
    private volatile String currentRoomId;
    private final Object writeLock = new Object();

    public BlockingClientSession(Socket socket) throws IOException {
        this(socket, new JsonMessageCodec());
    }

    public BlockingClientSession(Socket socket, MessageCodec codec) throws IOException {
        this.sessionId = UUID.randomUUID().toString();
        this.socket = Objects.requireNonNull(socket, "Socket must not be null");
        this.codec = codec != null ? codec : new JsonMessageCodec();
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getPlayerId() {
        return playerId;
    }

    @Override
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getCurrentRoomId() {
        return currentRoomId;
    }

    @Override
    public void setCurrentRoomId(String roomId) {
        this.currentRoomId = roomId;
    }

    @Override
    public boolean isAuthenticated() {
        return playerId != null && !playerId.isBlank();
    }

    @Override
    public void send(Message message) {
        if (!isConnected()) {
            log.warn("Cannot send message to disconnected session {}", sessionId);
            return;
        }
        try {
            String wireString = codec.encodeToString(message);
            synchronized (writeLock) {
                writer.write(wireString);
                writer.flush();
            }
            log.debug("Sent to session {} [{}]: {}", sessionId, username, message.getType());
        } catch (ProtocolException | IOException e) {
            log.error("Failed to send message to session {}: {}", sessionId, e.getMessage());
            close();
        }
    }

    @Override
    public void close() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.debug("Exception closing session socket {}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public String toString() {
        return "BlockingClientSession{" +
                "sessionId='" + sessionId + '\'' +
                ", playerId='" + playerId + '\'' +
                ", username='" + username + '\'' +
                ", currentRoomId='" + currentRoomId + '\'' +
                '}';
    }
}
