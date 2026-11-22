package com.quizbattle.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe accumulator for TCP byte stream fragmentation.
 *
 * <p>Buffers incoming byte chunks until newline ('\n') delimiters are encountered,
 * emitting complete JSON message lines and enforcing the maximum frame cap.</p>
 */
public class StreamFrameAccumulator {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final int maxFrameSizeBytes;

    public StreamFrameAccumulator() {
        this(JsonMessageCodec.DEFAULT_MAX_MESSAGE_SIZE);
    }

    public StreamFrameAccumulator(int maxFrameSizeBytes) {
        if (maxFrameSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFrameSizeBytes must be positive");
        }
        this.maxFrameSizeBytes = maxFrameSizeBytes;
    }

    /**
     * Feeds an incoming byte array chunk into the accumulator and returns any completed lines.
     *
     * @param chunk input bytes
     * @return list of complete string lines ready for JSON decoding
     * @throws ProtocolException if an un-delimited frame exceeds {@code maxFrameSizeBytes}
     */
    public synchronized List<String> feed(byte[] chunk) throws ProtocolException {
        if (chunk == null || chunk.length == 0) {
            return List.of();
        }
        return feed(chunk, 0, chunk.length);
    }

    /**
     * Feeds a slice of incoming bytes into the accumulator.
     */
    public synchronized List<String> feed(byte[] chunk, int offset, int length) throws ProtocolException {
        if (chunk == null || length <= 0) {
            return List.of();
        }
        List<String> completedLines = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            byte b = chunk[offset + i];
            if (b == '\n') {
                String line = buffer.toString(StandardCharsets.UTF_8).trim();
                buffer.reset();
                if (!line.isEmpty()) {
                    completedLines.add(line);
                }
            } else if (b != '\r') { // Ignore carriage return \r before \n
                buffer.write(b);
                if (buffer.size() > maxFrameSizeBytes) {
                    int exceeded = buffer.size();
                    buffer.reset();
                    throw new ProtocolException("Frame size " + exceeded +
                            " bytes exceeded maximum limit of " + maxFrameSizeBytes + " bytes");
                }
            }
        }
        return completedLines;
    }

    /**
     * Current size in bytes of the un-delimited fragment in the accumulator.
     */
    public synchronized int getPendingBytesCount() {
        return buffer.size();
    }

    /**
     * Clears any accumulated partial frame bytes.
     */
    public synchronized void reset() {
        buffer.reset();
    }
}
