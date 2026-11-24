package com.quizbattle.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 Server side of LAN discovery (UDP, java.net.DatagramSocket).
 Listens on UDP 5001 in its own thread and replies to each QUIZBATTLE_DISCOVER
 datagram with the TCP game port.
 */
public final class UdpDiscoveryResponder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UdpDiscoveryResponder.class);

    private final int requestedPort;
    private final int tcpPort;
    private final String serverName;
    private final String serverId = UUID.randomUUID().toString().substring(0, 8);
    private final IntSupplier openRoomsSupplier;
    private final IntSupplier onlinePlayersSupplier;
    private final AtomicLong requestsAnswered = new AtomicLong();

    private volatile DatagramSocket socket;
    private volatile boolean running;
    private Thread worker;

    public UdpDiscoveryResponder(int udpPort, int tcpPort, String serverName,
                                 IntSupplier openRoomsSupplier, IntSupplier onlinePlayersSupplier) {
        this.requestedPort = udpPort;
        this.tcpPort = tcpPort;
        this.serverName = serverName;
        this.openRoomsSupplier = openRoomsSupplier != null ? openRoomsSupplier : () -> 0;
        this.onlinePlayersSupplier = onlinePlayersSupplier != null ? onlinePlayersSupplier : () -> 0;
    }

    /** (1) Bind the UDP socket and start the listener thread. */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        DatagramSocket ds = new DatagramSocket(null);
        ds.setReuseAddress(true);
        ds.bind(new InetSocketAddress(requestedPort)); // wildcard address: also receives broadcasts
        socket = ds;
        running = true;

        worker = new Thread(this::listenLoop, "udp-discovery-responder");
        worker.setDaemon(true);
        worker.start();
        log.info("UDP discovery responder listening on UDP port {} (advertising TCP port {}, serverId={})",
                ds.getLocalPort(), tcpPort, serverId);
    }

    /** (2) Wait for datagrams forever (until close()). */
    private void listenLoop() {
        byte[] buffer = new byte[DiscoveryProtocol.MAX_DATAGRAM_BYTES];
        while (running) {
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(request); // blocks until a datagram arrives
                handle(request);
            } catch (SocketException e) {
                if (running) {
                    log.warn("UDP discovery socket error: {}", e.getMessage());
                }
                break; // socket closed by close() -> leave loop
            } catch (IOException | RuntimeException e) {
                log.warn("Ignoring bad discovery datagram: {}", e.getMessage());
            }
        }
        log.info("UDP discovery responder stopped.");
    }

    /** (3) Check the message and (4) reply to the sender. */
    private void handle(DatagramPacket request) throws IOException {
        String text = new String(request.getData(), request.getOffset(), request.getLength(), StandardCharsets.UTF_8);
        if (!DiscoveryProtocol.isDiscoveryRequest(text)) {
            log.debug("Ignored non-discovery datagram from {}", request.getSocketAddress());
            return;
        }

        byte[] reply = DiscoveryProtocol.encodeResponse(
                serverId, tcpPort, serverName, safeCount(openRoomsSupplier), safeCount(onlinePlayersSupplier));

        // Unicast the answer straight back to whoever asked (their IP + port)
        socket.send(new DatagramPacket(reply, reply.length, request.getSocketAddress()));
        long count = requestsAnswered.incrementAndGet();
        log.info("UDP discovery request #{} from {} -> replied with TCP port {}",
                count, request.getSocketAddress(), tcpPort);
    }

    private static int safeCount(IntSupplier supplier) {
        try {
            return Math.max(0, supplier.getAsInt());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public int getLocalPort() {
        DatagramSocket ds = socket;
        return ds != null ? ds.getLocalPort() : -1;
    }

    public String getServerId() {
        return serverId;
    }

    public long getRequestsAnswered() {
        return requestsAnswered.get();
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public synchronized void close() {
        running = false;
        DatagramSocket ds = socket;
        if (ds != null) {
            ds.close(); // unblocks receive() with a SocketException
        }
        if (worker != null) {
            try {
                worker.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}