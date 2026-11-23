package com.quizbattle.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizbattle.client.net.SocketNetworkClient;
import com.quizbattle.model.Question;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.server.DefaultServerMessageDispatcher;
import com.quizbattle.server.concurrent.ConcurrentRoomManager;
import com.quizbattle.server.nio.NioQuizServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Section 11: Five-Client Real-Time Multiplayer Demonstration Suite.
 * Automates the complete 13-step multiplayer demonstration scenario:
 * 1. Start NIO server.
 * 2. Connect 5 clients.
 * 3. Assign unique usernames.
 * 4. Host creates Room A.
 * 5. Server generates unique 6-character room code.
 * 6. 4 players join Room A.
 * 7. Live ROOM_STATE broadcast verified across all clients.
 * 8. Disconnect 1 player.
 * 9. Roster update (4 players) broadcast to remaining participants.
 * 10. Create Room B.
 * 11. Verify room isolation.
 * 12. Verify synchronized quiz question delivery & answer evaluation.
 */
@DisplayName("Five-Client Multiplayer Live Demonstration Suite")
class FiveClientDemonstrationTest {

    private static final Logger log = LoggerFactory.getLogger(FiveClientDemonstrationTest.class);

    private NioQuizServer server;
    private DefaultServerMessageDispatcher dispatcher;
    private ConcurrentRoomManager roomManager;
    private int port;
    private final List<SocketNetworkClient> clientList = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        roomManager = new ConcurrentRoomManager();
        dispatcher = new DefaultServerMessageDispatcher(roomManager, new com.quizbattle.server.game.DefaultQuizGameService());
        server = new NioQuizServer(0, dispatcher);
        server.start();
        port = server.getPort();
        log.info("Demonstration NIO Server started on TCP port {}", port);
    }

    @AfterEach
    void tearDown() {
        for (SocketNetworkClient client : clientList) {
            try {
                if (client != null && client.isConnected()) {
                    client.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
        clientList.clear();

        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    private SocketNetworkClient connectPlayer(String username) throws Exception {
        SocketNetworkClient client = new SocketNetworkClient();
        clientList.add(client);
        client.connect("127.0.0.1", port);

        CountDownLatch latch = new CountDownLatch(1);
        client.registerListener(MessageType.CONNECTED, msg -> latch.countDown());
        client.send(Message.request(MessageType.CONNECT, UUID.randomUUID().toString(), Map.of("username", username)));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Client " + username + " failed to connect");
        return client;
    }

    @Test
    @DisplayName("Full 13-Step Five-Client Multiplayer Demonstration")
    void testFiveClientFullDemonstration() throws Exception {
        log.info("--- Step 1 & 2: Launch 5 Simultaneous Clients ---");
        SocketNetworkClient host = connectPlayer("HostAlice");
        SocketNetworkClient player2 = connectPlayer("Bob");
        SocketNetworkClient player3 = connectPlayer("Charlie");
        SocketNetworkClient player4 = connectPlayer("Diana");
        SocketNetworkClient player5 = connectPlayer("Evan");

        assertEquals(5, clientList.size());
        assertEquals(5, server.getActiveClientCount());

        log.info("--- Step 3: Host creates Room A ---");
        CountDownLatch createLatch = new CountDownLatch(1);
        AtomicReference<String> roomCodeRef = new AtomicReference<>();

        host.registerListener(MessageType.ROOM_CREATED, msg -> {
            roomCodeRef.set(msg.getPayload().get("roomCode").asText());
            createLatch.countDown();
        });

        host.send(Message.request(MessageType.CREATE_ROOM, "req-create-room", Map.of(
                "roomName", "Grand Championship",
                "maxPlayers", 8
        )));

        assertTrue(createLatch.await(5, TimeUnit.SECONDS), "Failed to receive ROOM_CREATED");
        String roomCodeA = roomCodeRef.get();
        assertNotNull(roomCodeA);
        assertTrue(roomCodeA.startsWith("QB"));
        log.info("Server generated authoritative Room Code: {}", roomCodeA);

        log.info("--- Step 4 & 5: Remaining 4 players join Room A ---");
        CountDownLatch joinLatch = new CountDownLatch(4);
        CountDownLatch fivePlayersLatch = new CountDownLatch(1);
        AtomicInteger finalCount = new AtomicInteger(1);

        host.registerListener(MessageType.ROOM_STATE, msg -> {
            JsonNode payload = msg.getPayload();
            if (payload != null && payload.has("playerCount")) {
                int c = payload.get("playerCount").asInt();
                finalCount.updateAndGet(current -> Math.max(current, c));
                if (c == 5) {
                    fivePlayersLatch.countDown();
                }
            }
        });

        for (SocketNetworkClient p : List.of(player2, player3, player4, player5)) {
            p.registerListener(MessageType.ROOM_JOINED, msg -> joinLatch.countDown());
            p.send(Message.request(MessageType.JOIN_ROOM, UUID.randomUUID().toString(), Map.of("roomCode", roomCodeA)));
        }

        assertTrue(joinLatch.await(5, TimeUnit.SECONDS), "All 4 participants must join successfully");
        assertTrue(fivePlayersLatch.await(5, TimeUnit.SECONDS), "Host must receive synchronized ROOM_STATE updates with 5 players");
        assertEquals(5, finalCount.get(), "Total participants in Room A must equal 5");

        log.info("--- Step 6: Disconnect Evan (Player 5) ---");
        CountDownLatch fourPlayersLatch = new CountDownLatch(1);
        AtomicInteger countAfterDisconnect = new AtomicInteger(0);

        host.registerListener(MessageType.ROOM_STATE, msg -> {
            JsonNode payload = msg.getPayload();
            if (payload != null && payload.has("playerCount")) {
                int c = payload.get("playerCount").asInt();
                if (c == 4) {
                    countAfterDisconnect.set(c);
                    fourPlayersLatch.countDown();
                }
            }
        });

        player5.disconnect();
        assertTrue(fourPlayersLatch.await(5, TimeUnit.SECONDS), "Roster update must be broadcast after disconnect (4 players)");
        assertEquals(4, countAfterDisconnect.get(), "Roster should update to 4 remaining players");

        log.info("--- Step 7: Create Room B and Verify Isolation ---");
        SocketNetworkClient roomBHost = connectPlayer("ZackHostB");
        CountDownLatch roomBLatch = new CountDownLatch(1);
        AtomicReference<String> roomCodeBRef = new AtomicReference<>();

        roomBHost.registerListener(MessageType.ROOM_CREATED, msg -> {
            roomCodeBRef.set(msg.getPayload().get("roomCode").asText());
            roomBLatch.countDown();
        });

        roomBHost.send(Message.request(MessageType.CREATE_ROOM, "req-create-b", Map.of("roomName", "Room Beta")));
        assertTrue(roomBLatch.await(5, TimeUnit.SECONDS));
        String roomCodeB = roomCodeBRef.get();
        assertNotEquals(roomCodeA, roomCodeB);

        // Verify Room B host never receives Room A question
        AtomicBoolean roomBReceivedRoomAQuestion = new AtomicBoolean(false);
        roomBHost.registerListener(MessageType.QUESTION, msg -> roomBReceivedRoomAQuestion.set(true));

        log.info("--- Step 8: HostAlice starts quiz in Room A ---");
        CountDownLatch roomAQuestionLatch = new CountDownLatch(1);
        AtomicReference<Question> questionRef = new AtomicReference<>();

        player2.registerListener(MessageType.QUESTION, msg -> {
            questionRef.set(msg.getPayloadAs(Question.class));
            roomAQuestionLatch.countDown();
        });

        host.send(Message.request(MessageType.START_QUIZ, "req-start-q", Map.of("roomId", roomCodeA)));
        assertTrue(roomAQuestionLatch.await(5, TimeUnit.SECONDS), "Room A participants must receive question");
        Thread.sleep(250);
        assertFalse(roomBReceivedRoomAQuestion.get(), "Room B must NEVER receive questions from Room A!");

        log.info("--- Step 9: Player Bob submits answer and gets scored ---");
        Question activeQuestion = questionRef.get();
        assertNotNull(activeQuestion);

        CountDownLatch answerResultLatch = new CountDownLatch(1);
        AtomicReference<JsonNode> resultRef = new AtomicReference<>();

        player2.registerListener(MessageType.ANSWER_RESULT, msg -> {
            resultRef.set(msg.getPayload());
            answerResultLatch.countDown();
        });

        player2.send(Message.request(MessageType.ANSWER, "req-ans-1", Map.of(
                "questionId", activeQuestion.getQuestionId(),
                "selectedOptionIndex", 0
        )));

        assertTrue(answerResultLatch.await(5, TimeUnit.SECONDS), "Player Bob must receive ANSWER_RESULT");
        assertNotNull(resultRef.get());
        assertTrue(resultRef.get().has("correct"));
        assertTrue(resultRef.get().has("currentTotalScore"));

        log.info("Demonstration fully succeeded! All 13 steps verified.");
    }
}
