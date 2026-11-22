package com.quizbattle.protocol;

/**
 * Transport-neutral message codec contract for serializing and deserializing
 * protocol messages with newline framing and payload bounds enforcement.
 */
public interface MessageCodec {

    /**
     * Encodes a message into UTF-8 bytes terminated by newline delimiter ('\n').
     *
     * @param message the message envelope
     * @return encoded byte array including trailing newline
     * @throws ProtocolException if serialization fails or payload exceeds limits
     */
    byte[] encode(Message message) throws ProtocolException;

    /**
     * Encodes a message into a newline-terminated UTF-8 string.
     */
    String encodeToString(Message message) throws ProtocolException;

    /**
     * Decodes a single JSON line into a Message.
     *
     * @param line single message line (with or without trailing newline)
     * @return decoded Message instance
     * @throws ProtocolException if JSON is malformed, missing fields, or exceeds limits
     */
    Message decode(String line) throws ProtocolException;

    /**
     * Decodes a byte buffer into a Message.
     */
    Message decode(byte[] data) throws ProtocolException;

    /**
     * Returns the maximum allowable message size in bytes (e.g. 64 KiB).
     */
    int getMaxMessageSizeBytes();
}
