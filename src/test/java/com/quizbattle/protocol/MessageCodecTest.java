package com.quizbattle.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageCodec Framing & Protocol Smoke Tests")
class MessageCodecTest {

    private MessageCodec codec;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        codec = new JsonMessageCodec();
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Encode and decode request message with newline delimiter")
    void testEncodeDecodeRequest() throws ProtocolException {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("username", "TestUser");

        Message original = Message.request(MessageType.CONNECT, "req-42", payload);
        String encoded = codec.encodeToString(original);

        assertTrue(encoded.endsWith("\n"), "Encoded wire output must end with newline delimiter");

        Message decoded = codec.decode(encoded);
        assertEquals(MessageType.CONNECT, decoded.getType());
        assertEquals("req-42", decoded.getRequestId());
        assertNotNull(decoded.getPayload());
        assertEquals("TestUser", decoded.getPayload().get("username").asText());
    }

    @Test
    @DisplayName("Encode and decode event message without requestId")
    void testEncodeDecodeEvent() throws ProtocolException {
        Message original = Message.event(MessageType.START_QUIZ, Map.of("roomId", "room-123"));
        String encoded = codec.encodeToString(original);

        Message decoded = codec.decode(encoded);
        assertEquals(MessageType.START_QUIZ, decoded.getType());
        assertNull(decoded.getRequestId());
        assertEquals("room-123", decoded.getPayload().get("roomId").asText());
    }

    @Test
    @DisplayName("Throws ProtocolException when message exceeds 64 KiB limit")
    void testMessageSizeExceeded() {
        MessageCodec smallCodec = new JsonMessageCodec(1024); // 1 KiB limit

        String largeData = "X".repeat(2048);
        Message hugeMessage = Message.event(MessageType.ERROR, Map.of("data", largeData));

        assertThrows(ProtocolException.class, () -> smallCodec.encodeToString(hugeMessage));
    }

    @Test
    @DisplayName("Throws ProtocolException when parsing malformed JSON")
    void testMalformedJson() {
        String badJson = "{ \"type\": \"CONNECT\", malformed";
        assertThrows(ProtocolException.class, () -> codec.decode(badJson));
    }
}
