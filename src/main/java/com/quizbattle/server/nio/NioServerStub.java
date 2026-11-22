package com.quizbattle.server.nio;

import com.quizbattle.server.QuizServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Architectural stub for Member 4: Non-Blocking I/O (NIO Selector + SocketChannel) Server.
 *
 * <p>Contract Responsibilities for Member 4:
 * <ul>
 *   <li>Manage {@code ServerSocketChannel} and registered {@code Selector}.</li>
 *   <li>Handle {@code OP_ACCEPT}, {@code OP_READ}, {@code OP_WRITE} events.</li>
 *   <li>Accumulate fragmented TCP frames in per-connection {@code ByteBuffer} attachments.</li>
 *   <li>Dispatch parsed {@code Message} instances across worker threads safely.</li>
 * </ul>
 * </p>
 */
public class NioServerStub implements QuizServer {

    private static final Logger log = LoggerFactory.getLogger(NioServerStub.class);

    private final int port;
    private volatile boolean running = false;

    public NioServerStub(int port) {
        this.port = port;
    }

    @Override
    public void start() throws IOException {
        log.info("NioServerStub ready for Member 4 implementation on port {}", port);
        this.running = true;
    }

    @Override
    public void stop() {
        log.info("Stopping NioServerStub");
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
