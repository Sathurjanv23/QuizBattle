package com.quizbattle.discovery;

import com.quizbattle.app.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 Client side of LAN discovery (UDP broadcast, java.net.DatagramSocket).
 Blocks for up to the timeout: call it from a background thread, never the JavaFX thread.
 */
public final class UdpDiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(UdpDiscoveryClient.class);

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private final int discoveryPort;

    public UdpDiscoveryClient(int discoveryPort) {
        this.discoveryPort = discoveryPort;
    }

    public UdpDiscoveryClient() {
        this(AppConfig.getDiscoveryPort());
    }

    /** Broadcast on every local network plus localhost. */
    public List<DiscoveredServer> discover(Duration timeout) {
        return discover(timeout, defaultTargets());
    }

    public List<DiscoveredServer> discover(Duration timeout, Collection<InetAddress> targets) {
        Map<String, DiscoveredServer> found = new LinkedHashMap<>();
        byte[] request = DiscoveryProtocol.encodeRequest();

        // (1) Random free local port. Replies come back to it.
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true); // required to send to 255.255.255.255 / x.x.x.255

            // (2) Send the request to every target
            long startNanos = System.nanoTime();
            int sent = 0;
            for (InetAddress target : targets) {
                try {
                    socket.send(new DatagramPacket(request, request.length, target, discoveryPort));
                    sent++;
                } catch (IOException e) {
                    // e.g. "Network is unreachable" on an interface that is down; try the others
                    log.debug("Could not send discovery to {}: {}", target, e.getMessage());
                }
            }
            if (sent == 0) {
                log.warn("UDP discovery: no request could be sent.");
                return List.of();
            }

            // (3) Collect replies until the deadline
            long deadline = startNanos + timeout.toNanos();
            byte[] buffer = new byte[DiscoveryProtocol.MAX_DATAGRAM_BYTES];

            while (true) {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
                if (remainingMs <= 0) {
                    break;
                }
                socket.setSoTimeout((int) Math.max(1, remainingMs));

                DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(reply);
                } catch (SocketTimeoutException e) {
                    break; // time is up
                }

                long rttMs = (System.nanoTime() - startNanos) / 1_000_000;
                String text = new String(reply.getData(), reply.getOffset(), reply.getLength(), StandardCharsets.UTF_8);
                String sourceHost = reply.getAddress().getHostAddress();

                Optional<DiscoveredServer> parsed = DiscoveryProtocol.parseResponse(text, sourceHost, rttMs);
                if (parsed.isEmpty()) {
                    continue; // not a QuizBattle reply
                }
                merge(found, parsed.get());
            }
        } catch (IOException e) {
            log.warn("UDP discovery failed: {}", e.getMessage());
        }

        log.info("UDP discovery finished: {} server(s) found", found.size());
        return new ArrayList<>(found.values());
    }

    /** (4) One server can answer twice (localhost + broadcast). Keep one, prefer the LAN IP. */
    private static void merge(Map<String, DiscoveredServer> found, DiscoveredServer server) {
        String key = server.serverId() + ":" + server.tcpPort();
        DiscoveredServer existing = found.get(key);
        if (existing == null) {
            found.put(key, server);
        } else if (existing.isLoopback() && !server.isLoopback()) {
            found.put(key, existing.withHost(server.host()));
        }
    }

    /** 127.0.0.1, 255.255.255.255 and the broadcast address of every active network. */
    public static List<InetAddress> defaultTargets() {
        Set<InetAddress> targets = new LinkedHashSet<>();
        targets.add(InetAddress.getLoopbackAddress());
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress address : nic.getInterfaceAddresses()) {
                    InetAddress broadcast = address.getBroadcast(); // e.g. 192.168.1.255 (IPv4 only)
                    if (broadcast != null) {
                        targets.add(broadcast);
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Could not list network interfaces: {}", e.getMessage());
        }
        return new ArrayList<>(targets);
    }

    /** Terminal demo: scan the LAN and print what was found. */
    public static void main(String[] args) {
        UdpDiscoveryClient client = new UdpDiscoveryClient();
        System.out.println("Broadcasting QUIZBATTLE_DISCOVER on UDP port " + client.discoveryPort + " to:");
        defaultTargets().forEach(t -> System.out.println("   -> " + t.getHostAddress()));

        List<DiscoveredServer> servers = client.discover(DEFAULT_TIMEOUT);
        if (servers.isEmpty()) {
            System.out.println("No QuizBattle server answered within " + DEFAULT_TIMEOUT.toMillis()
                    + " ms. (Is the server running? Is UDP broadcast blocked on this network?)");
        } else {
            System.out.println("Found " + servers.size() + " server(s):");
            servers.forEach(s -> System.out.println("   * " + s));
        }
    }
}