package com.quizbattle.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 UDP discovery tests. Uses loopback unicast so they pass even where
 broadcast is blocked (CI runners, campus Wi-Fi).
 */
@DisplayName("Member 4 - UDP LAN Discovery Tests")
class UdpDiscoveryTest {

    private static final Duration TIMEOUT = Duration.ofMillis(800);

    private UdpDiscoveryResponder responder;
    private List<InetAddress> loopback;

    @BeforeEach
    void setUp() throws Exception {
        // UDP port 0 = any free port, so tests never clash with a running server on 5001
        responder = new UdpDiscoveryResponder(0, 5000, "Test Server", () -> 2, () -> 3);
        responder.start();
        loopback = List.of(InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void tearDown() {
        responder.close();
    }

    @Test
    @DisplayName("Client discovers server and receives its TCP port, name and live counts")
    void discoversServerOnLoopback() {
        UdpDiscoveryClient client = new UdpDiscoveryClient(responder.getLocalPort());

        List<DiscoveredServer> servers = client.discover(TIMEOUT, loopback);

        assertEquals(1, servers.size());
        DiscoveredServer server = servers.get(0);
        assertEquals("127.0.0.1", server.host());
        assertEquals(5000, server.tcpPort());
        assertEquals("Test Server", server.serverName());
        assertEquals(2, server.openRooms());
        assertEquals(3, server.onlinePlayers());
        assertEquals(responder.getServerId(), server.serverId());
        assertEquals(1, responder.getRequestsAnswered());
    }

    @Test
    @DisplayName("No server listening: returns an empty list after the timeout (no exception)")
    void emptyResultWhenNoServer() throws Exception {
        int unusedPort;
        try (DatagramSocket probe = new DatagramSocket()) {
            unusedPort = probe.getLocalPort();
        }
        UdpDiscoveryClient client = new UdpDiscoveryClient(unusedPort);

        long start = System.nanoTime();
        List<DiscoveredServer> servers = client.discover(Duration.ofMillis(400), loopback);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(servers.isEmpty());
        assertTrue(elapsedMs < 2_000, "discovery must respect its timeout");
    }

    @Test
    @DisplayName("Malformed datagrams are ignored and the responder keeps working")
    void ignoresMalformedDatagram() throws Exception {
        try (DatagramSocket raw = new DatagramSocket()) {
            raw.setSoTimeout(300);
            byte[] junk = "HELLO_NOT_QUIZBATTLE".getBytes(StandardCharsets.UTF_8);
            raw.send(new DatagramPacket(junk, junk.length, InetAddress.getLoopbackAddress(), responder.getLocalPort()));

            byte[] buffer = new byte[512];
            assertThrows(SocketTimeoutException.class, () -> raw.receive(new DatagramPacket(buffer, buffer.length)));
        }

        List<DiscoveredServer> servers = new UdpDiscoveryClient(responder.getLocalPort()).discover(TIMEOUT, loopback);
        assertEquals(1, servers.size(), "responder must still answer valid requests");
    }

    @Test
    @DisplayName("Five clients scanning at the same time all find the server")
    void fiveConcurrentClients() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<List<DiscoveredServer>>> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(pool.submit(() -> new UdpDiscoveryClient(responder.getLocalPort()).discover(TIMEOUT, loopback)));
            }
            for (Future<List<DiscoveredServer>> result : results) {
                assertEquals(1, result.get().size());
            }
            assertEquals(5, responder.getRequestsAnswered());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Protocol rejects invalid replies and keeps separators out of fields")
    void protocolValidation() {
        assertTrue(DiscoveryProtocol.isDiscoveryRequest("QUIZBATTLE_DISCOVER|2.0"));
        assertFalse(DiscoveryProtocol.isDiscoveryRequest("GET / HTTP/1.1"));

        assertTrue(DiscoveryProtocol.parseResponse("garbage", "10.0.0.1", 1).isEmpty());
        assertTrue(DiscoveryProtocol.parseResponse("QUIZBATTLE_SERVER|2.0|id|99999|Name|0|0", "10.0.0.1", 1).isEmpty());

        String reply = new String(DiscoveryProtocol.encodeResponse("abc", 5000, "My|Server", 1, 4), StandardCharsets.UTF_8);
        DiscoveredServer parsed = DiscoveryProtocol.parseResponse(reply, "192.168.1.20", 7).orElseThrow();
        assertEquals("My/Server", parsed.serverName());
        assertEquals("192.168.1.20", parsed.host());
        assertEquals(5000, parsed.tcpPort());
    }
}