package com.quizbattle.app;

/**
 * Central system configuration defaults and environment overrides.
 */
public final class AppConfig {

    public static final int DEFAULT_SERVER_PORT = 5000;
    public static final int DEFAULT_DISCOVERY_PORT = 5001;
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 64 * 1024; // 64 KiB
    public static final String PROTOCOL_VERSION = "2.0";

    private AppConfig() {
    }

    public static int getServerPort() {
        String prop = System.getProperty("quizbattle.server.port",
                System.getenv().getOrDefault("QUIZBATTLE_SERVER_PORT", String.valueOf(DEFAULT_SERVER_PORT)));
        try {
            return Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            return DEFAULT_SERVER_PORT;
        }
    }

    public static int getDiscoveryPort() {
        String prop = System.getProperty("quizbattle.discovery.port",
                System.getenv().getOrDefault("QUIZBATTLE_DISCOVERY_PORT", String.valueOf(DEFAULT_DISCOVERY_PORT)));
        try {
            return Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            return DEFAULT_DISCOVERY_PORT;
        }
    }

    public static int getMaxMessageBytes() {
        String prop = System.getProperty("quizbattle.buffer.max-bytes",
                System.getenv().getOrDefault("QUIZBATTLE_MAX_BYTES", String.valueOf(DEFAULT_MAX_MESSAGE_BYTES)));
        try {
            return Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_MESSAGE_BYTES;
        }
    }
}
