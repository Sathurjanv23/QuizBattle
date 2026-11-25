package com.quizbattle.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizbattle.client.net.SocketNetworkClient;
import com.quizbattle.model.Question;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.server.DefaultServerMessageDispatcher;
import com.quizbattle.server.concurrent.ConcurrentRoomManager;
import com.quizbattle.server.game.DefaultQuizGameService;
import com.quizbattle.server.nio.NioQuizServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full ten-question, five-client scored quiz over the real NIO server.
 *
 * <p>Every wait is a blocking-queue poll with a timeout (an event wait, not a sleep).</p>
 */
@DisplayName("Five-client ten-question scored quiz end-to-end (NIO)")
class FiveClientScoredQuizTest {

    private static final MessageType[] TRACKED = {
            MessageType.CONNECTED, MessageType.ROOM_CREATED, MessageType.ROOM_JOINED, MessageType.ROOM_STATE,
            MessageType.QUESTION, MessageType.ANSWER_RESULT, MessageType.QUESTION_CLOSED,
            MessageType.LEADERBOARD, MessageType.QUIZ_FINISHED, MessageType.ERROR
    };

    /** A test client with one queue per message type. */
    private static final class Player {
        final String name;
        final SocketNetworkClient client = new SocketNetworkClient();
        final Map<MessageType, BlockingQueue<JsonNode>> inbox = new EnumMap<>(MessageType.class);
        String playerId;

        Player(String name) {
            this.name = name;
            for (MessageType t : TRACKED) {
                BlockingQueue<JsonNode> q = new LinkedBlockingQueue<>();
                inbox.put(t, q);
                client.registerListener(t, m -> q.add(m.getPayload() != null ? m.getPayload() : new ObjectMapper().createObjectNode()));
            }
        }

        JsonNode await(MessageType type) throws InterruptedException {
            JsonNode n = inbox.get(type).poll(5, TimeUnit.SECONDS);
            assertNotNull(n, name + " timed out waiting for " + type);
            return n;
        }

        void send(MessageType type, Map<String, ?> payload) {
            client.send(Message.request(type, UUID.randomUUID().toString(), payload));
        }

        JsonNode answer(String questionId, int option) throws InterruptedException {
            send(MessageType.ANSWER, Map.of("questionId", questionId, "selectedOptionIndex", option));
            return await(MessageType.ANSWER_RESULT);
        }
    }

    private NioQuizServer server;
    private final List<Player> players = new ArrayList<>();
    private Map<String, Integer> answerKey;

    @BeforeEach
    void setUp() throws Exception {
        DefaultServerMessageDispatcher dispatcher =
                new DefaultServerMessageDispatcher(new ConcurrentRoomManager(), new DefaultQuizGameService());
        server = new NioQuizServer(0, dispatcher);
        server.start();

        answerKey = new HashMap<>();
        try (InputStream in = getClass().getResourceAsStream("/questions/java_questions.json")) {
            List<Question> qs = new ObjectMapper().readValue(in, new TypeReference<List<Question>>() {});
            qs.forEach(q -> answerKey.put(q.getQuestionId(), q.getCorrectOptionIndex()));
        }
        assertEquals(10, answerKey.size(), "Java bank should have 10 questions");
    }

    @AfterEach
    void tearDown() {
        for (Player p : players) {
            try {
                if (p.client.isConnected()) p.client.disconnect();
            } catch (Exception ignored) {
            }
        }
        if (server != null && server.isRunning()) server.stop();
    }

    private Player connect(String name) throws Exception {
        Player p = new Player(name);
        players.add(p);
        p.client.connect("127.0.0.1", server.getPort());
        p.send(MessageType.CONNECT, Map.of("username", name));
        p.playerId = p.await(MessageType.CONNECTED).get("playerId").asText();
        return p;
    }

    private int right(String qid) {
        return answerKey.get(qid);
    }

    private int wrong(String qid) {
        return (answerKey.get(qid) + 1) % 4;
    }

    @Test
    @DisplayName("5 clients, 10 questions: timed auto-progression, answer validation, and final ranking")
    void fullScoredQuiz() throws Exception {
        Player alice = connect("Alice");
        Player bob = connect("Bob");
        Player charlie = connect("Charlie");
        Player diana = connect("Diana");
        Player evan = connect("Evan");

        alice.send(MessageType.CREATE_ROOM, Map.of("roomName", "Finals"));
        String code = alice.await(MessageType.ROOM_CREATED).get("roomCode").asText();
        for (Player p : List.of(bob, charlie, diana, evan)) {
            p.send(MessageType.JOIN_ROOM, Map.of("roomCode", code));
            p.await(MessageType.ROOM_JOINED);
        }

        // The legacy manual-advance request is rejected for non-hosts.
        bob.send(MessageType.NEXT_QUESTION, Map.of());
        assertEquals("FORBIDDEN", bob.await(MessageType.ERROR).get("code").asText());

        alice.send(MessageType.START_QUIZ, Map.of("roomId", code, "category", "Java"));

        List<Player> connected = new ArrayList<>(List.of(alice, bob, charlie, diana, evan));
        for (int qNo = 1; qNo <= 10; qNo++) {
            String qid = null;
            long startTime = 0;
            long deadline = 0;
            for (Player p : connected) {
                JsonNode q = p.await(MessageType.QUESTION);
                assertFalse(q.has("correctOptionIndex"), "answer must not leak to clients");
                assertEquals(10, q.get("pointValue").asInt(), "clients see the fixed 10 points");
                assertEquals(qNo, q.get("questionNumber").asInt());
                assertEquals(10, q.get("totalQuestions").asInt());
                if (qid == null) qid = q.get("questionId").asText();
                assertEquals(qid, q.get("questionId").asText(), p.name + " got a different question");
                if (startTime == 0) {
                    startTime = q.get("startTimeMillis").asLong();
                    deadline = q.get("deadlineMillis").asLong();
                }
                assertEquals(startTime, q.get("startTimeMillis").asLong(), "room shares one start time");
                assertEquals(deadline, q.get("deadlineMillis").asLong(), "room shares one deadline");
            }
            assertEquals(20_000, deadline - startTime, "server starts a 20-second deadline");
            if (qNo == 1) {
                alice.send(MessageType.NEXT_QUESTION, Map.of("questionId", qid));
                assertEquals("AUTOMATIC_PROGRESS", alice.await(MessageType.ERROR).get("code").asText());
            }

            // Alice: always right. Bob: right on odd questions. Charlie: always wrong.
            // Diana: right until she disconnects after Q2. Evan: right on Q1, then wrong.
            assertTrue(alice.answer(qid, right(qid)).get("accepted").asBoolean());
            JsonNode bobResult = bob.answer(qid, qNo % 2 == 1 ? right(qid) : wrong(qid));
            assertTrue(bobResult.get("accepted").asBoolean());

            if (qNo == 1) {
                JsonNode dup = bob.answer(qid, right(qid));
                assertFalse(dup.get("accepted").asBoolean());
                assertEquals("DUPLICATE", dup.get("reason").asText());
                assertEquals(10, dup.get("currentTotalScore").asInt(), "duplicate must not add points");

                charlie.send(MessageType.ANSWER, Map.of("questionId", qid, "selectedOptionIndex", right(qid), "roomId", "ZZ9999"));
                JsonNode wrongRoom = charlie.await(MessageType.ANSWER_RESULT);
                assertEquals("WRONG_ROOM", wrongRoom.get("reason").asText());
            }
            assertTrue(charlie.answer(qid, wrong(qid)).get("accepted").asBoolean());
            if (connected.contains(diana)) {
                assertTrue(diana.answer(qid, right(qid)).get("accepted").asBoolean());
            }
            assertTrue(evan.answer(qid, qNo == 1 ? right(qid) : wrong(qid)).get("accepted").asBoolean());

            JsonNode aliceLeaderboard = null;
            for (Player p : connected) {
                JsonNode closed = p.await(MessageType.QUESTION_CLOSED);
                assertEquals(qid, closed.get("questionId").asText());
                assertEquals(right(qid), closed.get("correctOptionIndex").asInt());
                JsonNode lb = p.await(MessageType.LEADERBOARD);
                assertEquals(qNo, lb.get("questionsClosed").asInt());
                if (p == alice) aliceLeaderboard = lb;
            }

            if (qNo == 1) {
                JsonNode entries = aliceLeaderboard.get("entries");
                assertEquals(List.of("Alice", "Bob", "Diana", "Evan", "Charlie"), names(entries));
                assertEquals(List.of(1, 1, 1, 1, 5), ranks(entries));
            }

            // Evan answers after the close -> LATE
            if (qNo >= 2) {
                JsonNode late = evan.answer(qid, right(qid));
                assertEquals("LATE", late.get("reason").asText());
                assertEquals(10, late.get("currentTotalScore").asInt());
            }

            if (qNo == 2) {
                alice.inbox.get(MessageType.ROOM_STATE).clear(); // drop lobby-join states
                diana.client.disconnect();
                connected.remove(diana);
                awaitPlayerCount(alice, 4);
            }

            if (qNo < 10) {
                for (Player p : connected) {
                    JsonNode next = p.await(MessageType.QUESTION);
                    assertEquals(qNo + 1, next.get("questionNumber").asInt());
                    assertEquals(10, next.get("totalQuestions").asInt());
                    p.inbox.get(MessageType.QUESTION).add(next);
                }
            }
        }

        // After the last close the server finishes the quiz
        for (Player p : connected) {
            JsonNode fin = p.await(MessageType.QUIZ_FINISHED);
            assertEquals(10, fin.get("totalQuestions").asInt());
            assertEquals(List.of("Alice"), toList(fin.get("winners")));

            JsonNode standings = fin.get("finalStandings");
            assertEquals(List.of("Alice", "Bob", "Diana", "Evan", "Charlie"), names(standings));
            assertEquals(List.of(100, 50, 20, 10, 0), scores(standings));
            assertEquals(List.of(1, 2, 3, 4, 5), ranks(standings));
            assertEquals(List.of(10, 5, 2, 1, 0), correctCounts(standings));
            assertFalse(standings.get(2).get("connected").asBoolean(), "Diana is marked disconnected");
            assertTrue(standings.get(0).get("connected").asBoolean());
        }

        // Quiz is over: answers are LATE
        JsonNode afterEnd = bob.answer("java-110", 0);
        assertEquals("LATE", afterEnd.get("reason").asText());
    }

    private void awaitPlayerCount(Player host, int expected) throws InterruptedException {
        while (true) {
            JsonNode state = host.await(MessageType.ROOM_STATE);
            if (state.get("playerCount").asInt() == expected) return;
        }
    }

    private static List<String> names(JsonNode entries) {
        List<String> out = new ArrayList<>();
        entries.forEach(e -> out.add(e.get("username").asText()));
        return out;
    }

    private static List<Integer> scores(JsonNode entries) {
        List<Integer> out = new ArrayList<>();
        entries.forEach(e -> out.add(e.get("score").asInt()));
        return out;
    }

    private static List<Integer> ranks(JsonNode entries) {
        List<Integer> out = new ArrayList<>();
        entries.forEach(e -> out.add(e.get("rank").asInt()));
        return out;
    }

    private static List<Integer> correctCounts(JsonNode entries) {
        List<Integer> out = new ArrayList<>();
        entries.forEach(e -> out.add(e.get("correctCount").asInt()));
        return out;
    }

    private static List<String> toList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }
}
