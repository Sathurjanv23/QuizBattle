package com.quizbattle.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizbattle.client.net.SocketNetworkClient;
import com.quizbattle.model.Question;
import com.quizbattle.protocol.Message;
import com.quizbattle.protocol.MessageType;
import com.quizbattle.server.DefaultServerMessageDispatcher;
import com.quizbattle.server.blocking.BlockingQuizServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("End-to-End TCP Quiz Integration Tests")
class TcpQuizIntegrationTest {

    private BlockingQuizServer server;
    private DefaultServerMessageDispatcher dispatcher;
    private SocketNetworkClient client;
    private int serverPort;

    @BeforeEach
    void setUp() throws IOException {
        dispatcher = new DefaultServerMessageDispatcher();
        // Bind to port 0 to allocate an available ephemeral port
        server = new BlockingQuizServer(0, dispatcher);
        server.start();
        serverPort = server.getPort();
        assertTrue(serverPort > 0, "Server port should be assigned dynamically by OS");

        client = new SocketNetworkClient();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Full Quiz Flow: TCP Connect -> Join Room -> Receive Question -> Submit Answer -> Get Result")
    void testFullTcpQuizFlow() throws Exception {
        // Step 1: Connect TCP Socket
        client.connect("127.0.0.1", serverPort);
        assertTrue(client.isConnected(), "Client must report connected state");

        // Step 2: Handshake (CONNECT -> CONNECTED)
        CountDownLatch connectLatch = new CountDownLatch(1);
        AtomicReference<String> playerIdRef = new AtomicReference<>();

        client.registerListener(MessageType.CONNECTED, message -> {
            JsonNode payload = message.getPayload();
            if (payload != null && payload.has("playerId")) {
                playerIdRef.set(payload.get("playerId").asText());
            }
            connectLatch.countDown();
        });

        String connectReqId = UUID.randomUUID().toString();
        client.send(Message.request(MessageType.CONNECT, connectReqId, Map.of("username", "IntegrationTester")));

        assertTrue(connectLatch.await(5, TimeUnit.SECONDS), "Timed out waiting for CONNECTED response");
        assertNotNull(playerIdRef.get(), "Server must assign an authoritative playerId");
        assertTrue(playerIdRef.get().startsWith("usr-"), "PlayerId must follow 'usr-' convention");

        // Step 3: Join Default Room (JOIN_ROOM -> ROOM_JOINED + QUESTION)
        CountDownLatch joinLatch = new CountDownLatch(1);
        CountDownLatch questionLatch = new CountDownLatch(1);
        AtomicReference<Question> questionRef = new AtomicReference<>();

        client.registerListener(MessageType.ROOM_JOINED, message -> {
            joinLatch.countDown();
        });

        client.registerListener(MessageType.QUESTION, message -> {
            Question q = message.getPayloadAs(Question.class);
            questionRef.set(q);
            questionLatch.countDown();
        });

        String joinReqId = UUID.randomUUID().toString();
        client.send(Message.request(MessageType.JOIN_ROOM, joinReqId, Map.of("roomId", "room-default")));

        assertTrue(joinLatch.await(5, TimeUnit.SECONDS), "Timed out waiting for ROOM_JOINED response");
        assertTrue(questionLatch.await(5, TimeUnit.SECONDS), "Timed out waiting for QUESTION event");

        Question receivedQuestion = questionRef.get();
        assertNotNull(receivedQuestion, "Question must not be null");
        assertNotNull(receivedQuestion.getText(), "Question text must not be null");
        assertEquals(4, receivedQuestion.getOptions().size(), "Question must provide 4 multiple-choice options");
        assertNull(receivedQuestion.getCorrectOptionIndex(), "Correct answer index must be masked from client");

        // Step 4: Submit Answer (ANSWER -> ANSWER_RESULT)
        CountDownLatch answerLatch = new CountDownLatch(1);
        AtomicReference<JsonNode> answerResultRef = new AtomicReference<>();

        client.registerListener(MessageType.ANSWER_RESULT, message -> {
            answerResultRef.set(message.getPayload());
            answerLatch.countDown();
        });

        String answerReqId = UUID.randomUUID().toString();
        client.send(Message.request(MessageType.ANSWER, answerReqId, Map.of(
                "questionId", receivedQuestion.getQuestionId(),
                "selectedOptionIndex", 0
        )));

        assertTrue(answerLatch.await(5, TimeUnit.SECONDS), "Timed out waiting for ANSWER_RESULT response");
        JsonNode resultPayload = answerResultRef.get();
        assertNotNull(resultPayload, "Result payload must be present");
        assertTrue(resultPayload.has("correct"), "Payload must contain boolean 'correct'");
        assertTrue(resultPayload.has("pointsAwarded"), "Payload must contain 'pointsAwarded'");
        assertTrue(resultPayload.has("currentTotalScore"), "Payload must contain 'currentTotalScore'");
    }

    @Test
    @DisplayName("Error Handling: Submitting answer before connecting yields UNAUTHORIZED")
    void testUnauthorizedAnswerAttempt() throws Exception {
        client.connect("127.0.0.1", serverPort);

        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<String> errorCodeRef = new AtomicReference<>();

        client.registerListener(MessageType.ERROR, message -> {
            JsonNode payload = message.getPayload();
            if (payload != null && payload.has("code")) {
                errorCodeRef.set(payload.get("code").asText());
            }
            errorLatch.countDown();
        });

        // Send ANSWER directly without prior CONNECT handshake
        client.send(Message.request(MessageType.ANSWER, "req-unauth", Map.of(
                "questionId", "q-101",
                "selectedOptionIndex", 0
        )));

        assertTrue(errorLatch.await(5, TimeUnit.SECONDS), "Timed out waiting for ERROR response");
        assertEquals("UNAUTHORIZED", errorCodeRef.get());
    }
}
