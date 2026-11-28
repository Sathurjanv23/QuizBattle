package com.quizbattle.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizbattle.client.net.SocketNetworkClient;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.server.DefaultServerMessageDispatcher;
import com.quizbattle.server.concurrent.ConcurrentRoomManager;
import com.quizbattle.server.game.DefaultQuizGameService;
import com.quizbattle.server.nio.NioQuizServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionTimerIntegrationTest {

    private static final List<MessageType> TRACKED = List.of(
            MessageType.CONNECTED,
            MessageType.ROOM_CREATED,
            MessageType.QUESTION,
            MessageType.ANSWER_RESULT,
            MessageType.QUESTION_CLOSED,
            MessageType.QUIZ_FINISHED,
            MessageType.ERROR);

    private static final class TestPlayer {
        final SocketNetworkClient client = new SocketNetworkClient();
        final Map<MessageType, BlockingQueue<JsonNode>> inbox = new EnumMap<>(MessageType.class);

        TestPlayer() {
            for (MessageType type : TRACKED) {
                BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
                inbox.put(type, queue);
                client.registerListener(type, message -> queue.add(message.getPayload() != null
                        ? message.getPayload()
                        : new ObjectMapper().createObjectNode()));
            }
        }

        JsonNode await(MessageType type) throws InterruptedException {
            JsonNode payload = inbox.get(type).poll(5, TimeUnit.SECONDS);
            assertNotNull(payload, "Timed out waiting for " + type);
            return payload;
        }

        void send(MessageType type, Map<String, ?> payload) {
            client.send(Message.request(type, UUID.randomUUID().toString(), payload));
        }
    }

    private NioQuizServer server;
    private final List<TestPlayer> players = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        DefaultServerMessageDispatcher dispatcher = new DefaultServerMessageDispatcher(
                new ConcurrentRoomManager(),
                new DefaultQuizGameService("/questions/timer_questions.json"));
        server = new NioQuizServer(0, dispatcher);
        server.start();
    }

    @AfterEach
    void stopServer() {
        players.forEach(player -> {
            if (player.client.isConnected()) {
                player.client.disconnect();
            }
        });
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void separateRoomsTimeoutAndAdvanceIndependentlyToCompletion() throws Exception {
        TestPlayer alice = connect("Alice");
        TestPlayer bob = connect("Bob");
        createRoom(alice, "Alice's room");
        createRoom(bob, "Bob's room");

        alice.send(MessageType.START_QUIZ, Map.of("category", "General Trivia"));
        bob.send(MessageType.START_QUIZ, Map.of("category", "General Trivia"));

        JsonNode aliceFirstQuestion = alice.await(MessageType.QUESTION);
        JsonNode bobFirstQuestion = bob.await(MessageType.QUESTION);
        assertEquals("timer-101", aliceFirstQuestion.path("questionId").asText());
        assertEquals("timer-101", bobFirstQuestion.path("questionId").asText());
        assertEquals(1, aliceFirstQuestion.path("questionNumber").asInt());
        assertEquals(2, aliceFirstQuestion.path("totalQuestions").asInt());
        assertEquals(1_000,
                aliceFirstQuestion.path("deadlineMillis").asLong()
                        - aliceFirstQuestion.path("startTimeMillis").asLong());
        assertEquals(1_000,
                bobFirstQuestion.path("deadlineMillis").asLong()
                        - bobFirstQuestion.path("startTimeMillis").asLong());

        JsonNode aliceFirstClosed = alice.await(MessageType.QUESTION_CLOSED);
        JsonNode bobFirstClosed = bob.await(MessageType.QUESTION_CLOSED);
        assertEquals("timer-101", aliceFirstClosed.path("questionId").asText());
        assertEquals("timer-101", bobFirstClosed.path("questionId").asText());
        assertFalse(aliceFirstClosed.path("lastQuestion").asBoolean());
        assertFalse(bobFirstClosed.path("lastQuestion").asBoolean());

        alice.send(MessageType.ANSWER, Map.of("questionId", "timer-101", "selectedOptionIndex", 0));
        JsonNode lateAnswer = alice.await(MessageType.ANSWER_RESULT);
        assertFalse(lateAnswer.path("accepted").asBoolean());
        assertEquals("LATE", lateAnswer.path("reason").asText());

        JsonNode aliceSecondQuestion = alice.await(MessageType.QUESTION);
        JsonNode bobSecondQuestion = bob.await(MessageType.QUESTION);
        assertEquals("timer-102", aliceSecondQuestion.path("questionId").asText());
        assertEquals("timer-102", bobSecondQuestion.path("questionId").asText());
        assertEquals(2, aliceSecondQuestion.path("questionNumber").asInt());
        assertEquals(2, bobSecondQuestion.path("questionNumber").asInt());

        JsonNode aliceSecondClosed = alice.await(MessageType.QUESTION_CLOSED);
        JsonNode bobSecondClosed = bob.await(MessageType.QUESTION_CLOSED);
        assertTrue(aliceSecondClosed.path("lastQuestion").asBoolean());
        assertTrue(bobSecondClosed.path("lastQuestion").asBoolean());
        assertEquals(2, alice.await(MessageType.QUIZ_FINISHED).path("totalQuestions").asInt());
        assertEquals(2, bob.await(MessageType.QUIZ_FINISHED).path("totalQuestions").asInt());

        assertTrue(alice.inbox.get(MessageType.ERROR).isEmpty());
        assertTrue(bob.inbox.get(MessageType.ERROR).isEmpty());
    }

    @Test
    void showsAnswerResultBeforeAutomaticallySendingNextQuestion() throws Exception {
        TestPlayer alice = connect("Alice");
        createRoom(alice, "Alice's room");
        alice.send(MessageType.START_QUIZ, Map.of("category", "General Trivia"));

        JsonNode firstQuestion = alice.await(MessageType.QUESTION);
        alice.send(MessageType.ANSWER, Map.of(
                "questionId", firstQuestion.path("questionId").asText(),
                "selectedOptionIndex", 0));

        JsonNode answerResult = alice.await(MessageType.ANSWER_RESULT);
        assertTrue(answerResult.path("accepted").asBoolean());
        assertTrue(answerResult.has("correct"));
        assertEquals(firstQuestion.path("questionId").asText(), answerResult.path("questionId").asText());
        alice.await(MessageType.QUESTION_CLOSED);

        assertNull(alice.inbox.get(MessageType.QUESTION).poll(300, TimeUnit.MILLISECONDS),
                "The next question should wait while answer feedback is visible");
        JsonNode nextQuestion = alice.await(MessageType.QUESTION);
        assertFalse(firstQuestion.path("questionId").asText().equals(nextQuestion.path("questionId").asText()));
    }

    private TestPlayer connect(String username) throws Exception {
        TestPlayer player = new TestPlayer();
        players.add(player);
        player.client.connect("127.0.0.1", server.getPort());
        player.send(MessageType.CONNECT, Map.of("username", username));
        player.await(MessageType.CONNECTED);
        return player;
    }

    private String createRoom(TestPlayer player, String name) throws InterruptedException {
        player.send(MessageType.CREATE_ROOM, Map.of("roomName", name));
        return player.await(MessageType.ROOM_CREATED).path("roomCode").asText();
    }
}
