package com.quizbattle.protocol;

/**
 * Checked exception thrown when protocol serialization, deserialization,
 * framing, or length caps are violated.
 */
public class ProtocolException extends Exception {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
