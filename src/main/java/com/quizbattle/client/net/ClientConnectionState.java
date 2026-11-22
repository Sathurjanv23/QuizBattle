package com.quizbattle.client.net;

/**
 * State machine representing client network connectivity.
 */
public enum ClientConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}
