package com.quizbattle.server.blocking;

import com.quizbattle.server.QuizServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Architectural stub for Member 1: Traditional Multi-Threaded Blocking TCP Server (ServerSocket).
 *
 * <p>Contract Responsibilities for Member 1:
 * <ul>
 *   <li>Listen on TCP port 5000 using {@code ServerSocket}.</li>
 *   <li>Spawn a worker thread per client (or manage via thread pool).</li>
 *   <li>Read stream line-by-line using {@code BufferedReader} or byte buffer split on {@code \n}.</li>
 *   <li>Enforce maximum frame size cap (64 KiB).</li>
 *   <li>Pass messages to {@code ServerMessageDispatcher}.</li>
 * </ul>
 * </p>
 */
public class BlockingServerStub implements QuizServer {

    private static final Logger log = LoggerFactory.getLogger(BlockingServerStub.class);

    private final int port;
    private volatile boolean running = false;

    public BlockingServerStub(int port) {
        this.port = port;
    }

    @Override
    public void start() throws IOException {
        log.info("BlockingServerStub ready for Member 1 implementation on port {}", port);
        this.running = true;
    }

    @Override
    public void stop() {
        log.info("Stopping BlockingServerStub");
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void close() {
        stop();
    }
}
