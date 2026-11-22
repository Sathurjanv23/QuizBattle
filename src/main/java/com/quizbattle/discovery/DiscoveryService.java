package com.quizbattle.discovery;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Service contract for LAN UDP room beaconing and room auto-discovery.
 *
 * <p>Default Port: 5001 UDP.
 * In production, servers periodically broadcast room announcements or respond to broadcast queries.
 * Clients listen on UDP 5001 to discover servers on the local network without hardcoding IPs.</p>
 */
public interface DiscoveryService extends AutoCloseable {

    /**
     * Starts listening for LAN room discovery broadcasts.
     *
     * @param onBeaconDiscovered callback invoked when a room announcement is detected
     */
    void startListening(Consumer<DiscoveryBeacon> onBeaconDiscovered) throws IOException;

    /**
     * Broadcasts an announcement beacon over UDP.
     *
     * @param beacon beacon payload containing host, TCP port, and room information
     */
    void broadcastBeacon(DiscoveryBeacon beacon) throws IOException;

    /**
     * Stops UDP listening and broadcast tasks.
     */
    void stop();

    boolean isRunning();

    int getDiscoveryPort();
}
