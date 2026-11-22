package com.quizbattle.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;

/**
 * Standard Jackson-based implementation of {@link MessageCodec}.
 * Enforces newline-delimited framing and a strict message size cap (default 64 KiB).
 */
public class JsonMessageCodec implements MessageCodec {

    public static final int DEFAULT_MAX_MESSAGE_SIZE = 64 * 1024; // 64 KiB
    public static final String DELIMITER = "\n";

    private final ObjectMapper mapper;
    private final int maxMessageSizeBytes;

    public JsonMessageCodec() {
        this(DEFAULT_MAX_MESSAGE_SIZE);
    }

    public JsonMessageCodec(int maxMessageSizeBytes) {
        if (maxMessageSizeBytes <= 0) {
            throw new IllegalArgumentException("maxMessageSizeBytes must be positive");
        }
        this.maxMessageSizeBytes = maxMessageSizeBytes;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public byte[] encode(Message message) throws ProtocolException {
        return encodeToString(message).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String encodeToString(Message message) throws ProtocolException {
        if (message == null) {
            throw new ProtocolException("Cannot encode null message");
        }
        try {
            String json = mapper.writeValueAsString(message);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxMessageSizeBytes) {
                throw new ProtocolException("Message size " + bytes.length +
                        " bytes exceeds limit of " + maxMessageSizeBytes + " bytes");
            }
            return json + DELIMITER;
        } catch (JsonProcessingException e) {
            throw new ProtocolException("Failed to serialize message: " + e.getMessage(), e);
        }
    }

    @Override
    public Message decode(String line) throws ProtocolException {
        if (line == null) {
            throw new ProtocolException("Cannot decode null line");
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            throw new ProtocolException("Cannot decode empty line");
        }
        byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxMessageSizeBytes) {
            throw new ProtocolException("Message size " + bytes.length +
                    " bytes exceeds limit of " + maxMessageSizeBytes + " bytes");
        }
        try {
            return mapper.readValue(trimmed, Message.class);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("Malformed JSON message: " + e.getMessage(), e);
        }
    }

    @Override
    public Message decode(byte[] data) throws ProtocolException {
        if (data == null || data.length == 0) {
            throw new ProtocolException("Cannot decode empty byte array");
        }
        return decode(new String(data, StandardCharsets.UTF_8));
    }

    @Override
    public int getMaxMessageSizeBytes() {
        return maxMessageSizeBytes;
    }
}
