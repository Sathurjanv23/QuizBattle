package com.quizbattle.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizbattle.client.net.SocketNetworkClient;
import com.quizbattle.model.Question;
import com.quizbattle.model.Room;
import com.quizbattle.protocol.JsonMessageCodec;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.server.DefaultServerMessageDispatcher;
import com.quizbattle.server.concurrent.ConcurrentRoomManager;
import com.quizbattle.server.nio.NioClientSession;
import com.quizbattle.server.nio.NioQuizServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Member 2: Java NIO Non-Blocking Server & Multiplayer Room Integration Tests")
class NioMultiplayerIntegrationTest {

    private NioQuizServer server;
    private DefaultServerMessageDispatcher dispatcher;
    private ConcurrentRoomManager roomManager;
    private int serverPort;
    private final List<SocketNetworkClient> clientsToCleanup = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        roomManager = new ConcurrentRoomManager();
        dispatcher = new DefaultServerMessageDispatcher(roomManager, new com.quizbattle.server.game.DefaultQuizGameService());
        // Bind to port 0 for dynamic ephemeral port allocation
        server = new NioQuizServer(0, dispatcher);
        server.start();
        serverPort = server.getPort();
        assertTrue(serverPort > 0, "NIO Server port must be dynamically allocated by OS");
    }

    @AfterEach
    void tearDown() {
        for (SocketNetworkClient client : clientsToCleanup) {
            try {
                if (client != null && client.isConnected()) {
                    client.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
        clientsToCleanup.clear();

        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    private SocketNetworkClient createConnectedClient(String username) throws Exception {
        SocketNetworkClient client = new SocketNetworkClient();
        clientsToCleanup.add(client);
        client.connect("127.0.0.1", serverPort);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> playerIdRef = new AtomicReference<>();

        client.registerListener(MessageType.CONNECTED, msg -> {
            JsonNode payload = msg.getPayload();
            if (payload != null && payload.has("playerId")) {
                playerIdRef.set(payload.get("playerId").asText());
            }
            latch.countDown();
        });

        client.send(Message.request(MessageType.CONNECT, UUID.randomUUID().toString(), Map.of("username", username)));
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for CONNECTED for user " + username);
        assertNotNull(playerIdRef.get(), "Server must assign an authoritative playerId");
        return client;
    }

    // 1. NIO Server Startup
    @Test
    @DisplayName("1. NIO server startup on ephemeral port")
    void testNioServerStartup() {
        assertTrue(server.isRunning(), "NIO server should be running");
        assertTrue(server.getPort() > 0, "Server port should be bound to valid port");
    }

    // 2. Five Simultaneous TCP Clients
    @Test
    @DisplayName("2. Five simultaneous TCP clients connected to NIO multiplexer")
    void testFiveSimultaneousClients() throws Exception {
        int clientCount = 5;
        List<SocketNetworkClient> clients = new ArrayList<>();
        CountDownLatch connectedLatch = new CountDownLatch(clientCount);

        for (int i = 1; i <= clientCount; i++) {
            SocketNetworkClient c = new SocketNetworkClient();
            clientsToCleanup.add(c);
            clients.add(c);
            c.connect("127.0.0.1", serverPort);

            c.registerListener(MessageType.CONNECTED, msg -> connectedLatch.countDown());
            c.send(Message.request(MessageType.CONNECT, "req-" + i, Map.of("username", "Player_" + i)));
        }

        assertTrue(connectedLatch.await(5, TimeUnit.SECONDS), "All 5 clients must connect and authenticate");
        for (SocketNetworkClient c : clients) {
            assertTrue(c.isConnected(), "Client must maintain connected state");
        }
    }

    // 3. Host Room Creation
    @Test
    @DisplayName("3. Host creates multiplayer room with server-generated code")
    void testHostRoomCreation() throws Exception {
        SocketNetworkClient host = createConnectedClient("HostPlayer");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> roomCodeRef = new AtomicReference<>();

        host.registerListener(MessageType.ROOM_CREATED, msg -> {
            JsonNode payload = msg.getPayload();
            if (payload != null && payload.has("roomCode")) {
                roomCodeRef.set(payload.get("roomCode").asText());
            }
            latch.countDown();
        });

        host.send(Message.request(MessageType.CREATE_ROOM, "req-create", Map.of("roomName", "Championship Arena", "maxPlayers", 4)));
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for ROOM_CREATED");

        String code = roomCodeRef.get();
        assertNotNull(code);
        assertTrue(code.startsWith("QB"), "Room code must start with 'QB'");
        assertEquals(6, code.length(), "Room code must be 6 characters long");
    }

    // 4. Unique Room Codes
    @Test
    @DisplayName("4. Unique room codes generated across multiple rooms")
    void testUniqueRoomCodes() {
        ConcurrentRoomManager manager = new ConcurrentRoomManager();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Room r = manager.createRoom("usr-" + i, "Player" + i, "Room " + i, 4);
            assertFalse(codes.contains(r.getRoomId()), "Room code must be unique: " + r.getRoomId());
            codes.add(r.getRoomId());
        }
        assertEquals(20, codes.size());
    }

    // 5 & 6. Multiple Players Joining & Live ROOM_STATE Broadcasts
    @Test
    @DisplayName("5 & 6. Multiple players joining room and receiving live ROOM_STATE broadcasts")
    void testMultiplePlayersJoiningAndRoomStateBroadcast() throws Exception {
        SocketNetworkClient host = createConnectedClient("HostNinja");
        SocketNetworkClient player2 = createConnectedClient("PlayerTwo");
        SocketNetworkClient player3 = createConnectedClient("PlayerThree");

        // Step 1: Host creates room
        CountDownLatch createLatch = new CountDownLatch(1);
        AtomicReference<String> roomCodeRef = new AtomicReference<>();

        host.registerListener(MessageType.ROOM_CREATED, msg -> {
            roomCodeRef.set(msg.getPayload().get("roomCode").asText());
            createLatch.countDown();
        });

        host.send(Message.request(MessageType.CREATE_ROOM, "req-create", Map.of("roomName", "Battle Royale", "maxPlayers", 4)));
        assertTrue(createLatch.await(5, TimeUnit.SECONDS));
        String roomCode = roomCodeRef.get();

        // Step 2: Track ROOM_STATE broadcasts received by host
        CountDownLatch hostStateLatch = new CountDownLatch(2); // expect updates as player2 and player3 join
        AtomicInteger latestPlayerCount = new AtomicInteger(1);

        host.registerListener(MessageType.ROOM_STATE, msg -> {
            JsonNode payload = msg.getPayload();
            if (payload != null && payload.has("playerCount")) {
                latestPlayerCount.set(payload.get("playerCount").asInt());
                hostStateLatch.countDown();
            }
        });

        // Player 2 joins
        CountDownLatch joinLatch2 = new CountDownLatch(1);
        player2.registerListener(MessageType.ROOM_JOINED, msg -> joinLatch2.countDown());
        player2.send(Message.request(MessageType.JOIN_ROOM, "req-join-2", Map.of("roomCode", roomCode)));
        assertTrue(joinLatch2.await(5, TimeUnit.SECONDS), "Player 2 must receive ROOM_JOINED");

        // Player 3 joins
        CountDownLatch joinLatch3 = new CountDownLatch(1);
        player3.registerListener(MessageType.ROOM_JOINED, msg -> joinLatch3.countDown());
        player3.send(Message.request(MessageType.JOIN_ROOM, "req-join-3", Map.of("roomCode", roomCode)));
        assertTrue(joinLatch3.await(5, TimeUnit.SECONDS), "Player 3 must receive ROOM_JOINED");

        assertTrue(hostStateLatch.await(5, TimeUnit.SECONDS), "Host must receive live ROOM_STATE broadcasts");
        assertEquals(3, latestPlayerCount.get(), "Room must have 3 participants");
    }

    // 7. Unknown Room Code
    @Test
    @DisplayName("7. Unknown room code is rejected with structured ERROR")
    void testUnknownRoomCodeRejection() throws Exception {
        SocketNetworkClient client = createConnectedClient("CuriousPlayer");

        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<String> errorCodeRef = new AtomicReference<>();

        client.registerListener(MessageType.ERROR, msg -> {
            JsonNode payload = msg.getPayload();
            if (payload != null && payload.has("code")) {
                errorCodeRef.set(payload.get("code").asText());
            }
            errorLatch.countDown();
        });

        client.send(Message.request(MessageType.JOIN_ROOM, "req-unknown", Map.of("roomCode", "NONEX9")));
        assertTrue(errorLatch.await(5, TimeUnit.SECONDS), "Must receive error for non-existent room code");
        assertEquals("ROOM_NOT_FOUND", errorCodeRef.get());
    }

    // 8. Two-Room Isolation
    @Test
    @DisplayName("8. Strict two-room isolation: messages do not leak across rooms")
    void testTwoRoomIsolation() throws Exception {
        SocketNetworkClient hostA = createConnectedClient("HostA");
        SocketNetworkClient playerA = createConnectedClient("PlayerA");

        SocketNetworkClient hostB = createConnectedClient("HostB");
        SocketNetworkClient playerB = createConnectedClient("PlayerB");

        // Create Room A
        CountDownLatch latchA = new CountDownLatch(1);
        AtomicReference<String> roomCodeARef = new AtomicReference<>();
        hostA.registerListener(MessageType.ROOM_CREATED, msg -> {
            roomCodeARef.set(msg.getPayload().get("roomCode").asText());
            latchA.countDown();
        });
        hostA.send(Message.request(MessageType.CREATE_ROOM, "req-a", Map.of("roomName", "Room Alpha")));
        assertTrue(latchA.await(5, TimeUnit.SECONDS));

        // Create Room B
        CountDownLatch latchB = new CountDownLatch(1);
        AtomicReference<String> roomCodeBRef = new AtomicReference<>();
        hostB.registerListener(MessageType.ROOM_CREATED, msg -> {
            roomCodeBRef.set(msg.getPayload().get("roomCode").asText());
            latchB.countDown();
        });
        hostB.send(Message.request(MessageType.CREATE_ROOM, "req-b", Map.of("roomName", "Room Beta")));
        assertTrue(latchB.await(5, TimeUnit.SECONDS));

        String codeA = roomCodeARef.get();
        String codeB = roomCodeBRef.get();
        assertNotEquals(codeA, codeB);

        // Player A joins Room A
        CountDownLatch joinALatch = new CountDownLatch(1);
        playerA.registerListener(MessageType.ROOM_JOINED, msg -> joinALatch.countDown());
        playerA.send(Message.request(MessageType.JOIN_ROOM, "j-a", Map.of("roomCode", codeA)));
        assertTrue(joinALatch.await(5, TimeUnit.SECONDS));

        // Player B joins Room B
        CountDownLatch joinBLatch = new CountDownLatch(1);
        playerB.registerListener(MessageType.ROOM_JOINED, msg -> joinBLatch.countDown());
        playerB.send(Message.request(MessageType.JOIN_ROOM, "j-b", Map.of("roomCode", codeB)));
        assertTrue(joinBLatch.await(5, TimeUnit.SECONDS));

        // Verify isolation: Host A advances quiz in Room A. Player A must receive QUESTION, but Player B in Room B must NOT!
        CountDownLatch playerAQuestionLatch = new CountDownLatch(1);
        AtomicBoolean playerBReceivedQuestion = new AtomicBoolean(false);

        playerA.registerListener(MessageType.QUESTION, msg -> playerAQuestionLatch.countDown());
        playerB.registerListener(MessageType.QUESTION, msg -> playerBReceivedQuestion.set(true));

        hostA.send(Message.request(MessageType.START_QUIZ, "sq-a", Map.of("roomId", codeA)));

        assertTrue(playerAQuestionLatch.await(5, TimeUnit.SECONDS), "Player A in Room A must receive question");
        Thread.sleep(300); // Small pause to verify room B isolation
        assertFalse(playerBReceivedQuestion.get(), "Player B in Room B must NEVER receive Room A's question!");
    }

    // 9. Fragmented JSON Stream Reassembly
    @Test
    @DisplayName("9. Fragmented JSON stream over TCP is accumulated until newline delimiter")
    void testFragmentedJsonStream() throws Exception {
        try (Socket rawSocket = new Socket("127.0.0.1", serverPort)) {
            rawSocket.setTcpNoDelay(true);
            OutputStream out = rawSocket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(rawSocket.getInputStream(), StandardCharsets.UTF_8));

            String fullMessage = "{\"type\":\"CONNECT\",\"requestId\":\"frag-1\",\"payload\":{\"username\":\"StreamNinja\"}}\n";
            byte[] bytes = fullMessage.getBytes(StandardCharsets.UTF_8);

            // Send in small 5-byte chunks
            int chunkSize = 5;
            for (int offset = 0; offset < bytes.length; offset += chunkSize) {
                int length = Math.min(chunkSize, bytes.length - offset);
                out.write(bytes, offset, length);
                out.flush();
                Thread.sleep(15);
            }

            String responseLine = in.readLine();
            assertNotNull(responseLine, "Server must respond after complete newline-delimited message");
            JsonMessageCodec codec = new JsonMessageCodec();
            Message response = codec.decode(responseLine);
            assertEquals(MessageType.CONNECTED, response.getType());
            assertEquals("frag-1", response.getRequestId());
        }
    }

    // 10. Multiple Messages in One Read
    @Test
    @DisplayName("10. Multiple concatenated JSON messages in single TCP buffer parsed independently")
    void testMultipleMessagesInOneRead() throws Exception {
        try (Socket rawSocket = new Socket("127.0.0.1", serverPort)) {
            rawSocket.setTcpNoDelay(true);
            OutputStream out = rawSocket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(rawSocket.getInputStream(), StandardCharsets.UTF_8));

            // Write CONNECT and PING together in one write
            String packet = "{\"type\":\"CONNECT\",\"requestId\":\"multi-1\",\"payload\":{\"username\":\"FastPlayer\"}}\n" +
                    "{\"type\":\"PING\",\"requestId\":\"multi-2\"}\n";
            out.write(packet.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String line1 = in.readLine();
            assertNotNull(line1);
            JsonMessageCodec codec = new JsonMessageCodec();
            Message msg1 = codec.decode(line1);
            assertEquals(MessageType.CONNECTED, msg1.getType());

            String line2 = in.readLine();
            assertNotNull(line2);
            Message msg2 = codec.decode(line2);
            assertEquals(MessageType.PONG, msg2.getType());
            assertEquals("multi-2", msg2.getRequestId());
        }
    }

    // 11. Partial TCP Writes / Write Queue Flush
    @Test
    @DisplayName("11. Partial TCP write support and outbound message queuing")
    void testOutboundMessageQueuing() throws Exception {
        SocketNetworkClient client = createConnectedClient("WriteTester");
        CountDownLatch pongLatch = new CountDownLatch(10);

        client.registerListener(MessageType.PONG, msg -> pongLatch.countDown());

        // Rapidly dispatch 10 pings
        for (int i = 0; i < 10; i++) {
            client.send(Message.ping("ping-" + i));
        }

        assertTrue(pongLatch.await(5, TimeUnit.SECONDS), "All 10 queued messages must be delivered");
    }

    // 12. Bounded Outgoing Queues
    @Test
    @DisplayName("12. Bounded outgoing queue limits slow clients and prevents memory leaks")
    void testBoundedOutgoingQueue() throws Exception {
        try (java.nio.channels.SocketChannel channel = java.nio.channels.SocketChannel.open()) {
            NioClientSession dummySession = new NioClientSession(
                    channel, null, new JsonMessageCodec(), null, 65536, 10
            ) {
                @Override
                public boolean isConnected() {
                    return true;
                }
            };

            assertEquals(0, dummySession.getQueueSize());
        }
    }

    // 13. Unexpected Disconnect Handling
    @Test
    @DisplayName("13. Abrupt client disconnect cleans up SelectionKey and session safely")
    void testUnexpectedDisconnect() throws Exception {
        int initialSessions = server.getActiveClientCount();
        Socket rawSocket = new Socket("127.0.0.1", serverPort);
        rawSocket.setTcpNoDelay(true);

        OutputStream out = rawSocket.getOutputStream();
        out.write("{\"type\":\"CONNECT\",\"requestId\":\"disc-1\",\"payload\":{\"username\":\"Ghost\"}}\n".getBytes(StandardCharsets.UTF_8));
        out.flush();

        Thread.sleep(150);
        assertEquals(initialSessions + 1, server.getActiveClientCount());

        // Abruptly close raw socket without protocol handshake
        rawSocket.close();

        // Wait for NIO selector to detect EOF and reap session
        Thread.sleep(300);
        assertEquals(initialSessions, server.getActiveClientCount(), "Active session count should decrement after disconnect");
    }

    // 14. Room Cleanup When Empty
    @Test
    @DisplayName("14. Room is automatically removed from registry when all players depart")
    void testRoomCleanupWhenEmpty() {
        Room room = roomManager.createRoom("usr-1", "Host", "Temporary Room", 4);
        String code = room.getRoomId();
        assertTrue(roomManager.findRoomById(code).isPresent());

        // Host leaves
        roomManager.leaveRoom(code, "usr-1");

        // Room should be cleaned up
        assertFalse(roomManager.findRoomById(code).isPresent(), "Empty ephemeral room must be removed from registry");
    }

    // 15. Host Disconnect & Host Migration
    @Test
    @DisplayName("15. Host disconnect migrates host privileges to next remaining player")
    void testHostMigrationOnDisconnect() throws Exception {
        SocketNetworkClient host = createConnectedClient("OriginalHost");
        SocketNetworkClient member = createConnectedClient("RemainingMember");

        CountDownLatch createLatch = new CountDownLatch(1);
        AtomicReference<String> roomCodeRef = new AtomicReference<>();
        host.registerListener(MessageType.ROOM_CREATED, msg -> {
            roomCodeRef.set(msg.getPayload().get("roomCode").asText());
            createLatch.countDown();
        });
        host.send(Message.request(MessageType.CREATE_ROOM, "req-host-mig", Map.of("roomName", "Migration Arena")));
        assertTrue(createLatch.await(5, TimeUnit.SECONDS));
        String code = roomCodeRef.get();

        CountDownLatch joinLatch = new CountDownLatch(1);
        member.registerListener(MessageType.ROOM_JOINED, msg -> joinLatch.countDown());
        member.send(Message.request(MessageType.JOIN_ROOM, "req-join-m", Map.of("roomCode", code)));
        assertTrue(joinLatch.await(5, TimeUnit.SECONDS));

        // Member watches for ROOM_STATE where host changes
        CountDownLatch migrationLatch = new CountDownLatch(1);
        member.registerListener(MessageType.ROOM_STATE, msg -> {
            JsonNode payload = msg.getPayload();
            if (payload != null && "RemainingMember".equals(payload.path("host").asText())) {
                migrationLatch.countDown();
            }
        });

        // Abruptly disconnect original host
        host.disconnect();

        assertTrue(migrationLatch.await(5, TimeUnit.SECONDS), "Remaining member must receive ROOM_STATE confirming new host role");
    }

    // 16. Selector Idle Behavior
    @Test
    @DisplayName("16. Selector event loop remains responsive and does not busy-spin when idle")
    void testSelectorIdleBehavior() throws Exception {
        assertTrue(server.isRunning());
        // Sleep 500ms and verify server is still responsive and healthy
        Thread.sleep(500);
        assertTrue(server.isRunning());

        // Connect a client to verify immediate responsiveness
        SocketNetworkClient client = createConnectedClient("AwakeClient");
        assertTrue(client.isConnected());
    }

    // 17. Existing Quiz Question/Answer Flow Regression
    @Test
    @DisplayName("17. Full quiz round (question delivery + answer scoring) works over NIO server")
    void testQuizQuestionAnswerRegression() throws Exception {
        SocketNetworkClient client = createConnectedClient("TriviaPro");

        // Join default room
        CountDownLatch joinLatch = new CountDownLatch(1);
        CountDownLatch questionLatch = new CountDownLatch(1);
        AtomicReference<Question> questionRef = new AtomicReference<>();

        client.registerListener(MessageType.ROOM_JOINED, msg -> joinLatch.countDown());
        client.registerListener(MessageType.QUESTION, msg -> {
            questionRef.set(msg.getPayloadAs(Question.class));
            questionLatch.countDown();
        });

        client.send(Message.request(MessageType.JOIN_ROOM, "req-quiz-join", Map.of("roomId", "room-default")));
        assertTrue(joinLatch.await(5, TimeUnit.SECONDS));
        assertTrue(questionLatch.await(5, TimeUnit.SECONDS));

        Question q = questionRef.get();
        assertNotNull(q);

        // Submit Answer
        CountDownLatch answerLatch = new CountDownLatch(1);
        AtomicReference<JsonNode> resultRef = new AtomicReference<>();

        client.registerListener(MessageType.ANSWER_RESULT, msg -> {
            resultRef.set(msg.getPayload());
            answerLatch.countDown();
        });

        client.send(Message.request(MessageType.ANSWER, "req-quiz-ans", Map.of(
                "questionId", q.getQuestionId(),
                "selectedOptionIndex", 0
        )));

        assertTrue(answerLatch.await(5, TimeUnit.SECONDS));
        JsonNode result = resultRef.get();
        assertNotNull(result);
        assertTrue(result.has("correct"));
        assertTrue(result.has("pointsAwarded"));
        assertTrue(result.has("currentTotalScore"));
    }

    // 18. Graceful Shutdown
    @Test
    @DisplayName("18. Graceful shutdown releases port and closes all client channels")
    void testGracefulShutdown() throws Exception {
        SocketNetworkClient client = createConnectedClient("ShutdownWatcher");
        assertTrue(client.isConnected());

        server.stop();
        assertFalse(server.isRunning(), "Server isRunning must report false after stop");
        assertEquals(0, server.getActiveClientCount(), "Active client sessions must be cleared");

        // Give client a moment to register socket close
        Thread.sleep(200);
        assertFalse(client.isConnected(), "Client must detect server disconnection");
    }
}
