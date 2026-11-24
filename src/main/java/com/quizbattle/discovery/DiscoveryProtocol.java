package com.quizbattle.discovery;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 UDP discovery wire format.
 
 Client -> broadcast 255.255.255.255:5001   QUIZBATTLE_DISCOVER|2.0
 Server -> unicast back to the sender        QUIZBATTLE_SERVER|2.0|serverId|tcpPort|serverName|openRooms|onlinePlayers
 */
public final class DiscoveryProtocol {

    public static final String REQUEST_MAGIC = "QUIZBATTLE_DISCOVER";
    public static final String RESPONSE_MAGIC = "QUIZBATTLE_SERVER";
    public static final String VERSION = "2.0";
    public static final String SEPARATOR = "|";

    /** Datagrams larger than this are ignored. Discovery messages are well under 200 bytes. */
    public static final int MAX_DATAGRAM_BYTES = 512;

    private DiscoveryProtocol() {
    }

    // ---------- Client -> Server ----------

    public static byte[] encodeRequest() {
        return (REQUEST_MAGIC + SEPARATOR + VERSION).getBytes(StandardCharsets.UTF_8);
    }

    public static boolean isDiscoveryRequest(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.equals(REQUEST_MAGIC) || trimmed.startsWith(REQUEST_MAGIC + SEPARATOR);
    }

    // ---------- Server -> Client ----------

    public static byte[] encodeResponse(String serverId, int tcpPort, String serverName,
                                        int openRooms, int onlinePlayers) {
        String text = String.join(SEPARATOR,
                RESPONSE_MAGIC,
                VERSION,
                clean(serverId),
                String.valueOf(tcpPort),
                clean(serverName),
                String.valueOf(openRooms),
                String.valueOf(onlinePlayers));
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static Optional<DiscoveredServer> parseResponse(String text, String sourceHost, long responseTimeMs) {
        if (text == null || sourceHost == null) {
            return Optional.empty();
        }
        String[] parts = text.trim().split("\\|", -1);
        if (parts.length != 7 || !RESPONSE_MAGIC.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            int tcpPort = Integer.parseInt(parts[3]);
            if (tcpPort < 1 || tcpPort > 65535) {
                return Optional.empty();
            }
            return Optional.of(new DiscoveredServer(
                    parts[2],                      // serverId
                    parts[4],                      // serverName
                    sourceHost,                    // IP comes from the packet, not the text
                    tcpPort,
                    Integer.parseInt(parts[5]),    // openRooms
                    Integer.parseInt(parts[6]),    // onlinePlayers
                    parts[1],                      // version
                    responseTimeMs));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Removes "|" and control characters so a field cannot break the format. */
    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace(SEPARATOR, "/").replaceAll("\\p{Cntrl}", " ").trim();
    }
}