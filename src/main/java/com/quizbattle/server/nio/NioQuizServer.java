package com.quizbattle.server.nio;

import com.quizbattle.protocol.JsonMessageCodec;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageCodec;
import com.quizbattle.protocol.ProtocolException;
import com.quizbattle.server.ClientSession;
import com.quizbattle.server.QuizServer;
import com.quizbattle.server.ServerMessageDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * High-performance non-blocking TCP server engine utilizing Java NIO {@link Selector}
 * and {@link SocketChannel} multiplexing.
 *
 * <p>Key Architecture Principles:
 * <ul>
 *   <li>Single multiplexed selector loop serving hundreds of concurrent client connections.</li>
 *   <li>{@code OP_ACCEPT}: Accepts non-blocking sockets, assigns {@link NioClientSession}.</li>
 *   <li>{@code OP_READ}: Reads byte streams, buffers stream fragments, enforces 64 KiB framing.</li>
 *   <li>{@code OP_WRITE}: Asynchronous non-blocking write queue handling partial TCP socket writes.</li>
 *   <li>Game business logic is dispatched onto an asynchronous worker pool, never blocking the selector.</li>
 *   <li>Zero busy-spinning: clears {@code OP_WRITE} interest whenever send queues are drained.</li>
 * </ul>
 * </p>
 */
public class NioQuizServer implements QuizServer {

    private static final Logger log = LoggerFactory.getLogger(NioQuizServer.class);
    private static final int READ_BUFFER_SIZE = 8192;

    private final int requestedPort;
    private final ServerMessageDispatcher dispatcher;
    private final MessageCodec codec;
    private final Map<String, NioClientSession> activeSessions = new ConcurrentHashMap<>();
    private final Queue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Thread selectorThread;
    private ExecutorService workerPool;
    private volatile boolean running = false;
    private volatile int actualPort;

    public NioQuizServer(int port, ServerMessageDispatcher dispatcher) {
        this(port, dispatcher, new JsonMessageCodec());
    }

    public NioQuizServer(int port, ServerMessageDispatcher dispatcher, MessageCodec codec) {
        this.requestedPort = port;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.codec = codec != null ? codec : new JsonMessageCodec();
        this.actualPort = port;
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) {
            log.warn("NioQuizServer is already running on port {}", actualPort);
            return;
        }

        log.info("Initializing Java NIO Selector and ServerSocketChannel on port {}...", requestedPort);

        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverChannel.bind(new InetSocketAddress(requestedPort));

        // Retrieve actual local port (useful when binding to ephemeral port 0)
        actualPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        workerPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "quizbattle-nio-worker");
            t.setDaemon(true);
            return t;
        });

        running = true;
        selectorThread = new Thread(this::eventLoop, "quizbattle-nio-selector");
        selectorThread.setDaemon(true);
        selectorThread.start();

        log.info("NioQuizServer successfully listening on TCP port {} [Selector multiplexing active]", actualPort);
    }

    /**
     * Primary NIO Selector Event Loop.
     */
    private void eventLoop() {
        log.info("Java NIO Selector event loop started on port {}", actualPort);
        ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);

        while (running && selector.isOpen()) {
            try {
                // Execute any queued channel interest changes or cross-thread requests
                processPendingTasks();

                // Select ready operations with a 100ms timeout
                int readyCount = selector.select(100);

                processPendingTasks();

                if (readyCount == 0) {
                    continue;
                }

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        }
                        if (key.isValid() && key.isReadable()) {
                            handleRead(key, readBuffer);
                        }
                        if (key.isValid() && key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (CancelledKeyException e) {
                        log.debug("Selection key cancelled during processing.");
                    } catch (Exception e) {
                        log.error("Exception handling selection key: {}", e.getMessage(), e);
                        cleanupKey(key);
                    }
                }
            } catch (ClosedSelectorException e) {
                log.debug("Selector closed, terminating event loop.");
                break;
            } catch (Exception e) {
                if (running) {
                    log.error("Unexpected error in NIO event loop: {}", e.getMessage(), e);
                }
            }
        }
        log.info("Java NIO Selector event loop terminated.");
    }

    /**
     * Handles OP_ACCEPT: Accepts an incoming TCP connection, configures it non-blocking,
     * attaches an {@link NioClientSession}, and registers for OP_READ.
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = ssc.accept();

        if (clientChannel == null) {
            return;
        }

        clientChannel.configureBlocking(false);
        clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);

        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
        NioClientSession session = new NioClientSession(clientChannel, clientKey, codec, this);
        clientKey.attach(session);
        activeSessions.put(session.getSessionId(), session);

        log.info("Accepted NIO TCP client connection from {} -> Session: {}",
                clientChannel.getRemoteAddress(), session.getSessionId());

        // Notify dispatcher on worker pool
        workerPool.submit(() -> {
            try {
                dispatcher.onSessionConnected(session);
            } catch (Exception e) {
                log.error("Error in onSessionConnected for session {}: {}", session.getSessionId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Handles OP_READ: Reads raw byte chunks into ByteBuffer, accumulates JSON lines,
     * and offloads message handling to the worker pool.
     */
    private void handleRead(SelectionKey key, ByteBuffer readBuffer) {
        NioClientSession session = (NioClientSession) key.attachment();
        if (session == null || !session.isConnected()) {
            cleanupKey(key);
            return;
        }

        readBuffer.clear();
        int bytesRead;
        try {
            bytesRead = session.getChannel().read(readBuffer);
        } catch (IOException e) {
            log.info("TCP reset / disconnect on session {}: {}", session.getSessionId(), e.getMessage());
            handleDisconnect(session);
            return;
        }

        if (bytesRead == -1) {
            log.info("Client session {} received EOF (remote close).", session.getSessionId());
            handleDisconnect(session);
            return;
        }

        if (bytesRead > 0) {
            readBuffer.flip();
            byte[] chunk = new byte[readBuffer.remaining()];
            readBuffer.get(chunk);

            List<String> completedLines;
            try {
                completedLines = session.getAccumulator().feed(chunk);
            } catch (ProtocolException e) {
                log.warn("Frame cap violated by session {}: {}", session.getSessionId(), e.getMessage());
                session.send(Message.error(null, "FRAME_TOO_LARGE", e.getMessage()));
                return;
            }

            workerPool.submit(() -> {
                synchronized (session.getDispatchLock()) {
                    for (String line : completedLines) {
                        try {
                            Message message = codec.decode(line);
                            dispatcher.dispatch(session, message);
                        } catch (ProtocolException pe) {
                            log.warn("Malformed JSON message from session {}: {}", session.getSessionId(), pe.getMessage());
                            session.send(Message.error(null, "MALFORMED_MESSAGE", pe.getMessage()));
                        } catch (Exception ex) {
                            log.error("Error dispatching message for session {}: {}", session.getSessionId(), ex.getMessage(), ex);
                        }
                    }
                }
            });
        }
    }

    /**
     * Handles OP_WRITE: Flushes queued ByteBuffers to the client SocketChannel.
     * Clears OP_WRITE when the queue is fully drained to prevent busy-spinning.
     */
    private void handleWrite(SelectionKey key) {
        NioClientSession session = (NioClientSession) key.attachment();
        if (session == null || !session.isConnected()) {
            cleanupKey(key);
            return;
        }

        try {
            session.writePendingData();

            // If all queued messages have been written, disable OP_WRITE to prevent busy spinning
            if (!session.hasPendingWrites()) {
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            log.info("Write error to session {}: {}", session.getSessionId(), e.getMessage());
            handleDisconnect(session);
        }
    }

    /**
     * Requests write interest for a session. Thread-safe: can be called from worker
     * or game logic threads.
     */
    public void requestWrite(NioClientSession session) {
        if (!running || session == null || !session.isConnected()) {
            return;
        }

        submitTask(() -> {
            SelectionKey key = session.getSelectionKey();
            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        });
    }

    /**
     * Thread-safe task submission to the NIO selector thread.
     * Wakes up the selector immediately.
     */
    public void submitTask(Runnable task) {
        if (task == null) return;
        pendingTasks.offer(task);
        if (selector != null && selector.isOpen()) {
            selector.wakeup();
        }
    }

    private void processPendingTasks() {
        Runnable task;
        while ((task = pendingTasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error executing pending selector task: {}", e.getMessage(), e);
            }
        }
    }

    private void handleDisconnect(NioClientSession session) {
        if (session == null) return;
        activeSessions.remove(session.getSessionId());
        session.close();

        workerPool.submit(() -> {
            try {
                dispatcher.onSessionDisconnected(session);
            } catch (Exception e) {
                log.error("Error in onSessionDisconnected for session {}: {}", session.getSessionId(), e.getMessage(), e);
            }
        });
    }

    private void cleanupKey(SelectionKey key) {
        if (key != null) {
            try {
                Object attachment = key.attachment();
                if (attachment instanceof NioClientSession session) {
                    handleDisconnect(session);
                }
                key.cancel();
                if (key.channel() != null && key.channel().isOpen()) {
                    key.channel().close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        log.info("Stopping NioQuizServer on port {}...", actualPort);
        running = false;

        // Wake up selector loop so it terminates promptly
        if (selector != null && selector.isOpen()) {
            selector.wakeup();
        }

        // Close all client sessions
        for (NioClientSession session : activeSessions.values()) {
            session.close();
        }
        activeSessions.clear();

        // Close server socket channel
        if (serverChannel != null && serverChannel.isOpen()) {
            try {
                serverChannel.close();
            } catch (IOException e) {
                log.warn("Error closing ServerSocketChannel: {}", e.getMessage());
            }
        }

        // Close selector
        if (selector != null && selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException e) {
                log.warn("Error closing Selector: {}", e.getMessage());
            }
        }

        // Shutdown worker pool
        if (workerPool != null) {
            workerPool.shutdownNow();
            try {
                if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.debug("Worker pool did not terminate within timeout.");
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        dispatcher.close();
        log.info("NioQuizServer stopped successfully.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPort() {
        return actualPort;
    }

    public int getActiveClientCount() {
        return activeSessions.size();
    }

    public Map<String, NioClientSession> getActiveSessions() {
        return activeSessions;
    }

    @Override
    public void close() {
        stop();
    }
}
