package com.quizbattle.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * Standard protocol envelope for all QuizBattle 2.0 communications.
 *
 * <p>Wire Format:
 * <pre>
 * {"type":"CONNECT","requestId":"req-1","payload":{"username":"Alice"}}\n
 * </pre>
 *
 * Security Principle:
 * The client NEVER supplies trusted authoritative data (e.g. {@code score}, {@code playerId}).
 * The server associates every inbound message with the authenticated {@code ClientSession}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    private final MessageType type;
    private final String requestId;
    private final JsonNode payload;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonCreator
    public Message(
            @JsonProperty(value = "type", required = true) MessageType type,
            @JsonProperty("requestId") String requestId,
            @JsonProperty("payload") JsonNode payload) {
        this.type = Objects.requireNonNull(type, "MessageType cannot be null");
        this.requestId = requestId;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public String getRequestId() {
        return requestId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    /**
     * Extracts the payload deserialized into a target Java class.
     */
    public <T> T getPayloadAs(Class<T> clazz) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        return MAPPER.convertValue(payload, clazz);
    }

    // Static Factory Helpers

    public static Message request(MessageType type, String requestId, Object payloadObj) {
        JsonNode node = payloadObj == null ? null : MAPPER.valueToTree(payloadObj);
        return new Message(type, requestId, node);
    }

    public static Message event(MessageType type, Object payloadObj) {
        JsonNode node = payloadObj == null ? null : MAPPER.valueToTree(payloadObj);
        return new Message(type, null, node);
    }

    public static Message error(String requestId, String errorCode, String errorMessage) {
        ObjectNode errNode = MAPPER.createObjectNode();
        errNode.put("code", errorCode);
        errNode.put("message", errorMessage);
        return new Message(MessageType.ERROR, requestId, errNode);
    }

    public static Message ping(String requestId) {
        return new Message(MessageType.PING, requestId, null);
    }

    public static Message pong(String requestId) {
        return new Message(MessageType.PONG, requestId, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message message)) return false;
        return type == message.type &&
                Objects.equals(requestId, message.requestId) &&
                Objects.equals(payload, message.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, requestId, payload);
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", requestId='" + requestId + '\'' +
                ", payload=" + payload +
                '}';
    }
}
