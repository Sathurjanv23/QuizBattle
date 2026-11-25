package com.quizbattle.server.blocking;

import com.quizbattle.protocol.JsonMessageCodec;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageCodec;
import com.quizbattle.protocol.ProtocolException;
import com.quizbattle.server.ClientSession;
import com.quizbattle.server.QuizServer;
import com.quizbattle.server.ServerMessageDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Multi-threaded reference blocking TCP server using {@link ServerSocket}.
 *
 * <p>Architectural Scope:
 * <ul>
 *   <li>Evaluated concept for Member 1: Classic socket programming with worker threads.</li>
 *   <li>Transport-neutral design: zero game business logic lives here. All incoming messages
 *       are handed off directly to {@link ServerMessageDispatcher}.</li>
 *   <li>Easily switchable: Member 2/4 can replace this implementation with a non-blocking
 *       NIO server without altering game services or client protocol code.</li>
 * </ul>
 * </p>
 */
public class BlockingQuizServer implements QuizServer {

    private static final Logger log = LoggerFactory.getLogger(BlockingQuizServer.class);

    private final int port;
    private final ServerMessageDispatcher dispatcher;
    private final MessageCodec codec;
    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private ExecutorService workerPool;
    private Thread acceptThread;
    private volatile boolean running = false;

    public BlockingQuizServer(int port, ServerMessageDispatcher dispatcher) {
        this(port, dispatcher, new JsonMessageCodec());
    }

    public BlockingQuizServer(int port, ServerMessageDispatcher dispatcher, MessageCodec codec) {
        this.port = port;
        this.dispatcher = dispatcher;
        this.codec = codec != null ? codec : new JsonMessageCodec();
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) {
            log.warn("BlockingQuizServer is already running on port {}", port);
            return;
        }

        serverSocket = new ServerSocket(port);
        // If port 0 was passed, get the ephemeral assigned port
        int actualPort = serverSocket.getLocalPort();
        running = true;
        workerPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "quizbattle-server-worker");
            t.setDaemon(true);
            return t;
        });

        acceptThread = new Thread(this::acceptLoop, "quizbattle-server-acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();

        log.info("BlockingQuizServer started listening on TCP port {}", actualPort);
    }

    private void acceptLoop() {
        log.info("Acceptor loop started on port {}", serverSocket.getLocalPort());
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                log.info("Accepted incoming TCP connection from {}", clientSocket.getRemoteSocketAddress());
                workerPool.submit(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                if (!running) {
                    log.debug("ServerSocket closed, terminating accept loop.");
                } else {
                    log.error("Socket exception in accept loop: {}", e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    log.error("I/O error accepting client connection: {}", e.getMessage());
                }
            }
        }
        log.info("Acceptor loop terminated.");
    }

    private void handleClient(Socket clientSocket) {
        ClientSession session = null;
        try {
            session = new BlockingClientSession(clientSocket, codec);
            activeSessions.put(session.getSessionId(), session);
            dispatcher.onSessionConnected(session);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    Message message = codec.decode(line);
                    dispatcher.dispatch(session, message);
                } catch (ProtocolException e) {
                    log.warn("Protocol error from session {}: {}", session.getSessionId(), e.getMessage());
                    session.send(Message.error(null, "MALFORMED_MESSAGE", e.getMessage()));
                }
            }
            log.info("Client session {} disconnected normally (EOF).", session.getSessionId());
        } catch (IOException e) {
            log.info("Connection reset or I/O error on session {}: {}",
                    session != null ? session.getSessionId() : "unknown", e.getMessage());
        } finally {
            if (session != null) {
                activeSessions.remove(session.getSessionId());
                dispatcher.onSessionDisconnected(session);
                session.close();
            }
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        log.info("Stopping BlockingQuizServer on port {}", port);
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("Error closing server socket: {}", e.getMessage());
        }

        for (ClientSession session : activeSessions.values()) {
            session.close();
        }
        activeSessions.clear();

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
        log.info("BlockingQuizServer stopped.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPort() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    @Override
    public void close() {
        stop();
    }
}
