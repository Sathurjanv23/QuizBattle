package com.quizbattle.app;

import com.quizbattle.server.DefaultServerMessageDispatcher;
import com.quizbattle.server.QuizServer;
import com.quizbattle.server.blocking.BlockingQuizServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server application bootstrap entry point.
 *
 * <p>Initializes the reference blocking TCP server for Member 1,
 * wired to the transport-neutral {@link DefaultServerMessageDispatcher}.
 * Member 2/4 can swap {@link BlockingQuizServer} with an NIO server implementation
 * without modifying game logic or protocol handling.</p>
 */
public class QuizBattleServerApp {

    private static final Logger log = LoggerFactory.getLogger(QuizBattleServerApp.class);

    public static void main(String[] args) {
        int port = AppConfig.getServerPort();
        int discoveryPort = AppConfig.getDiscoveryPort();

        log.info("====================================================");
        log.info("Starting QuizBattle 2.0 Server (Member 1 TCP Reference)");
        log.info("TCP Port: {} | UDP Discovery Port: {}", port, discoveryPort);
        log.info("Max Message Framing Cap: {} bytes (64 KiB)", AppConfig.getMaxMessageBytes());
        log.info("Transport: Blocking ServerSocket (Switchable)");
        log.info("====================================================");

        DefaultServerMessageDispatcher dispatcher = new DefaultServerMessageDispatcher();
        QuizServer server = new BlockingQuizServer(port, dispatcher);

        try {
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered, stopping server...");
                server.stop();
            }));

            log.info("QuizBattle Server running and accepting connections on port {}.", port);
        } catch (Exception e) {
            log.error("Fatal error starting QuizBattle server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
