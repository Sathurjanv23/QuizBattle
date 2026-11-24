package com.quizbattle.app;

import com.quizbattle.discovery.UdpDiscoveryResponder;
import com.quizbattle.server.DefaultServerMessageDispatcher;
import com.quizbattle.server.QuizServer;
import com.quizbattle.server.blocking.BlockingQuizServer;
import com.quizbattle.server.nio.NioQuizServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.InetAddress;


/**
 * Server application bootstrap entry point.
 *
 * <p>Initializes the high-performance Java NIO non-blocking server by default (Member 2),
 * wired to the transport-neutral {@link DefaultServerMessageDispatcher}.
 * Blocking TCP reference implementation (Member 1) remains selectable via
 * {@code -Dquizbattle.server.transport=blocking}.</p>
 */
public class QuizBattleServerApp {

    private static final Logger log = LoggerFactory.getLogger(QuizBattleServerApp.class);

    public static void main(String[] args) {
        int port = AppConfig.getServerPort();
        int discoveryPort = AppConfig.getDiscoveryPort();
        String transport = System.getProperty("quizbattle.server.transport", "nio").trim().toLowerCase();

        log.info("====================================================");
        log.info("Starting QuizBattle 2.0 Server (Member 2 Java NIO Non-Blocking Engine)");
        log.info("TCP Port: {} | UDP Discovery Port: {}", port, discoveryPort);
        log.info("Max Message Framing Cap: {} bytes (64 KiB)", AppConfig.getMaxMessageBytes());
        log.info("Transport: {} [Default: Java NIO Non-Blocking]", "blocking".equals(transport) ? "Blocking ServerSocket" : "Java NIO Selector");
        log.info("====================================================");

        DefaultServerMessageDispatcher dispatcher = new DefaultServerMessageDispatcher();
        QuizServer server = "blocking".equals(transport)
                ? new BlockingQuizServer(port, dispatcher)
                : new NioQuizServer(port, dispatcher);

        // UDP discovery responder (UDP 5001) runs beside the TCP server
        UdpDiscoveryResponder discoveryResponder = new UdpDiscoveryResponder(
                discoveryPort,
                port,
                resolveServerName(),
                () -> dispatcher.getRoomService().listAvailableRooms().size(),
                () -> dispatcher.getActiveSessions().size());

        try {
            server.start();

            try {
                discoveryResponder.start();
            } catch (IOException e) {
                // Discovery is optional: players can still connect by typing the IP
                log.warn("UDP discovery unavailable on port {} ({}). Clients must connect manually.",
                        discoveryPort, e.getMessage());
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered, stopping server...");
                discoveryResponder.close();
                server.stop();
            }));

            log.info("QuizBattle Server running and accepting connections on port {}.", port);

            // Keep main thread alive while server is running
            while (server.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Fatal error starting QuizBattle server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    /** Name shown in the clients' LAN server list. Override with -Dquizbattle.server.name=... */
    private static String resolveServerName() {
        String configured = System.getProperty("quizbattle.server.name");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        try {
            return "QuizBattle @ " + InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "QuizBattle Server";
        }
    }
}
