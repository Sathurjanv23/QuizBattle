package com.quizbattle.app;

import com.quizbattle.server.QuizServer;
import com.quizbattle.server.blocking.BlockingServerStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server application bootstrap entry point.
 *
 * <p>Execution Command:
 * <pre>
 * mvn compile exec:java -Dexec.mainClass="com.quizbattle.app.QuizBattleServerApp"
 * </pre>
 * </p>
 */
public class QuizBattleServerApp {

    private static final Logger log = LoggerFactory.getLogger(QuizBattleServerApp.class);

    public static void main(String[] args) {
        int port = AppConfig.getServerPort();
        int discoveryPort = AppConfig.getDiscoveryPort();

        log.info("====================================================");
        log.info("Starting QuizBattle 2.0 Server Architecture Scaffold");
        log.info("TCP Port: {} | UDP Discovery Port: {}", port, discoveryPort);
        log.info("Max Message Framing Cap: {} bytes (64 KiB)", AppConfig.getMaxMessageBytes());
        log.info("Ready for Member 1 Blocking / Member 4 NIO server binding");
        log.info("====================================================");

        QuizServer server = new BlockingServerStub(port);
        try {
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered, stopping server...");
                server.stop();
            }));

            log.info("QuizBattle Server scaffold initialized successfully.");
        } catch (Exception e) {
            log.error("Fatal error starting QuizBattle server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
