package com.quizbattle.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageCodec Framing, Protocol & TCP Fragmentation Tests")
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

    @Test
    @DisplayName("TCP Stream Fragmentation: Reassemble partial chunks split across network packets")
    void testStreamFragmentationReassembly() throws ProtocolException {
        StreamFrameAccumulator accumulator = new StreamFrameAccumulator();

        String json1 = "{\"type\":\"CONNECT\",\"requestId\":\"r-1\",\"payload\":{\"username\":\"Bob\"}}\n";
        byte[] fullBytes = json1.getBytes(StandardCharsets.UTF_8);

        // Split into 3 arbitrary chunks: bytes 0..10, 11..25, 26..end
        byte[] chunk1 = new byte[10];
        byte[] chunk2 = new byte[15];
        byte[] chunk3 = new byte[fullBytes.length - 25];

        System.arraycopy(fullBytes, 0, chunk1, 0, 10);
        System.arraycopy(fullBytes, 10, chunk2, 0, 15);
        System.arraycopy(fullBytes, 25, chunk3, 0, chunk3.length);

        List<String> res1 = accumulator.feed(chunk1);
        assertTrue(res1.isEmpty(), "Chunk 1 is incomplete, should return no lines");

        List<String> res2 = accumulator.feed(chunk2);
        assertTrue(res2.isEmpty(), "Chunk 2 is incomplete, should return no lines");

        List<String> res3 = accumulator.feed(chunk3);
        assertEquals(1, res3.size(), "Chunk 3 finishes the line with '\\n'");

        Message msg = codec.decode(res3.get(0));
        assertEquals(MessageType.CONNECT, msg.getType());
        assertEquals("Bob", msg.getPayload().get("username").asText());
    }

    @Test
    @DisplayName("TCP Stream Multi-Message: Parse multiple messages concatenated in a single packet")
    void testStreamMultiMessageInSingleChunk() throws ProtocolException {
        StreamFrameAccumulator accumulator = new StreamFrameAccumulator();

        String multi = "{\"type\":\"PING\",\"requestId\":\"p-1\"}\n{\"type\":\"PONG\",\"requestId\":\"p-2\"}\n";
        byte[] chunk = multi.getBytes(StandardCharsets.UTF_8);

        List<String> lines = accumulator.feed(chunk);
        assertEquals(2, lines.size(), "Must detect exactly two lines");

        Message m1 = codec.decode(lines.get(0));
        assertEquals(MessageType.PING, m1.getType());
        assertEquals("p-1", m1.getRequestId());

        Message m2 = codec.decode(lines.get(1));
        assertEquals(MessageType.PONG, m2.getType());
        assertEquals("p-2", m2.getRequestId());
    }

    @Test
    @DisplayName("TCP Stream Accumulator rejects frame exceeding buffer size")
    void testStreamAccumulatorExceedsLimit() {
        StreamFrameAccumulator smallAccumulator = new StreamFrameAccumulator(128); // 128 bytes limit
        byte[] largeChunkWithoutNewline = "A".repeat(200).getBytes(StandardCharsets.UTF_8);

        assertThrows(ProtocolException.class, () -> smallAccumulator.feed(largeChunkWithoutNewline));
    }
}
