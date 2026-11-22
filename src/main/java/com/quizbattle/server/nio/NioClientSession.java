package com.quizbattle.server.nio;

import com.quizbattle.protocol.JsonMessageCodec;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageCodec;
import com.quizbattle.protocol.ProtocolException;
import com.quizbattle.protocol.StreamFrameAccumulator;
import com.quizbattle.server.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-blocking Java NIO implementation of {@link ClientSession}.
 *
 * <p>Architectural guarantees:
 * <ul>
 *   <li>Attached directly to the {@link SelectionKey} in the NIO Selector loop.</li>
 *   <li>Thread-safe bounded outgoing queue with backpressure detection for slow clients.</li>
 *   <li>Preserves partial writes: unsent bytes remain in {@code currentOutgoingBuffer}.</li>
 *   <li>Maintains streaming byte fragmentation accumulator for incoming TCP stream.</li>
 *   <li>Server-authoritative session state: strictly bounds playerId and room context.</li>
 * </ul>
 * </p>
 */
public class NioClientSession implements ClientSession {

    private static final Logger log = LoggerFactory.getLogger(NioClientSession.class);
    public static final int DEFAULT_MAX_OUTBOUND_QUEUE_SIZE = 256;

    private final String sessionId;
    private final SocketChannel channel;
    private final SelectionKey selectionKey;
    private final MessageCodec codec;
    private final NioQuizServer server;
    private final StreamFrameAccumulator accumulator;
    private final int maxQueueSize;

    private final Queue<ByteBuffer> outgoingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);

    private ByteBuffer currentOutgoingBuffer = null;
    private volatile String playerId;
    private volatile String username;
    private volatile String currentRoomId;
    private volatile boolean closed = false;

    public NioClientSession(SocketChannel channel, SelectionKey selectionKey, MessageCodec codec, NioQuizServer server) {
        this(channel, selectionKey, codec, server, JsonMessageCodec.DEFAULT_MAX_MESSAGE_SIZE, DEFAULT_MAX_OUTBOUND_QUEUE_SIZE);
    }

    public NioClientSession(
            SocketChannel channel,
            SelectionKey selectionKey,
            MessageCodec codec,
            NioQuizServer server,
            int maxFrameSizeBytes,
            int maxQueueSize) {
        this.sessionId = UUID.randomUUID().toString();
        this.channel = Objects.requireNonNull(channel, "SocketChannel must not be null");
        this.selectionKey = selectionKey;
        this.codec = codec != null ? codec : new JsonMessageCodec();
        this.server = server;
        this.accumulator = new StreamFrameAccumulator(maxFrameSizeBytes);
        this.maxQueueSize = maxQueueSize > 0 ? maxQueueSize : DEFAULT_MAX_OUTBOUND_QUEUE_SIZE;
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
            log.warn("Cannot send message to disconnected NIO session {}", sessionId);
            return;
        }

        try {
            byte[] bytes = codec.encode(message);

            if (queueSize.get() >= maxQueueSize) {
                log.warn("Session {} outgoing queue exceeded capacity limit ({}). Dropping connection to prevent memory leak.",
                        sessionId, maxQueueSize);
                close();
                return;
            }

            outgoingQueue.offer(ByteBuffer.wrap(bytes));
            queueSize.incrementAndGet();

            if (server != null) {
                server.requestWrite(this);
            }
        } catch (ProtocolException e) {
            log.error("Failed to serialize message for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Flushes queued outgoing ByteBuffers to the non-blocking SocketChannel.
     * Supports partial TCP writes without busy-spinning.
     */
    public synchronized void writePendingData() throws IOException {
        if (!isConnected()) {
            return;
        }

        while (true) {
            if (currentOutgoingBuffer == null || !currentOutgoingBuffer.hasRemaining()) {
                currentOutgoingBuffer = outgoingQueue.poll();
                if (currentOutgoingBuffer != null) {
                    queueSize.decrementAndGet();
                } else {
                    // Outbound queue fully flushed
                    break;
                }
            }

            channel.write(currentOutgoingBuffer);

            if (currentOutgoingBuffer.hasRemaining()) {
                // Partial write: TCP send buffer is full; yield until OP_WRITE triggers again
                log.debug("Partial TCP write on session {}. Remaining bytes: {}",
                        sessionId, currentOutgoingBuffer.remaining());
                break;
            }
        }
    }

    public boolean hasPendingWrites() {
        return (currentOutgoingBuffer != null && currentOutgoingBuffer.hasRemaining()) || !outgoingQueue.isEmpty();
    }

    private final Object dispatchLock = new Object();

    public Object getDispatchLock() {
        return dispatchLock;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public StreamFrameAccumulator getAccumulator() {
        return accumulator;
    }

    public int getQueueSize() {
        return queueSize.get();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        try {
            if (selectionKey != null) {
                selectionKey.cancel();
            }
        } catch (Exception ignored) {
        }

        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {
        }

        outgoingQueue.clear();
        queueSize.set(0);
        currentOutgoingBuffer = null;
        log.debug("NioClientSession {} closed and resources cleaned up.", sessionId);
    }

    @Override
    public boolean isConnected() {
        return !closed && channel != null && channel.isOpen() && channel.isConnected();
    }

    @Override
    public String toString() {
        return "NioClientSession{" +
                "sessionId='" + sessionId + '\'' +
                ", playerId='" + playerId + '\'' +
                ", username='" + username + '\'' +
                ", currentRoomId='" + currentRoomId + '\'' +
                ", queueSize=" + queueSize.get() +
                '}';
    }
}
