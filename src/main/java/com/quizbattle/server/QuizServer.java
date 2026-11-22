package com.quizbattle.server;

import java.io.IOException;

/**
 * Lifecycle contract for QuizBattle server transport implementations.
 */
public interface QuizServer extends AutoCloseable {

    /**
     * Starts the server listening on the configured TCP port.
     */
    void start() throws IOException;

    /**
     * Stops the server and releases network and thread pool resources.
     */
    void stop();

    boolean isRunning();

    int getPort();
}
