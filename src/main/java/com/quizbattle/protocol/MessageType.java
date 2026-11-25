package com.quizbattle.protocol;

/**
 * Enumeration of all protocol message event and command types for QuizBattle 2.0.
 *
 * <p>Framing: Every message over TCP is newline-delimited UTF-8 JSON.
 * Request-response correlations use the optional {@code requestId} field.</p>
 */
public enum MessageType {
    // Connection Lifecycle
    CONNECT,
    CONNECTED,

    // Room Management
    CREATE_ROOM,
    ROOM_CREATED,
    JOIN_ROOM,
    ROOM_JOINED,
    ROOM_STATE,
    LEAVE_ROOM,
    ROOM_LEFT,

    // Quiz Lifecycle
    START_QUIZ,
    /** Legacy command; quiz questions now close and advance automatically on the server. */
    NEXT_QUESTION,
    QUESTION,
    ANSWER,
    ANSWER_RESULT,
    QUESTION_CLOSED,

    // Game Results & Standings
    LEADERBOARD,
    QUIZ_FINISHED,

    // System & Diagnostics
    ERROR,
    PING,
    PONG
}
