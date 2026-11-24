package com.quizbattle.discovery;

/**
 A QuizBattle server found on the local network through UDP discovery.
 */
public record DiscoveredServer(
        String serverId,
        String serverName,
        String host,
        int tcpPort,
        int openRooms,
        int onlinePlayers,
        String version,
        long responseTimeMs) {

    public boolean isLoopback() {
        return host.startsWith("127.") || host.equals("0:0:0:0:0:0:0:1") || host.equals("::1");
    }

    DiscoveredServer withHost(String newHost) {
        return new DiscoveredServer(serverId, serverName, newHost, tcpPort, openRooms,
                onlinePlayers, version, responseTimeMs);
    }

    /** Text shown in the client's server list. */
    @Override
    public String toString() {
        return String.format("%s  -  %s:%d   |  %d room(s), %d player(s)  |  %d ms",
                serverName, host, tcpPort, openRooms, onlinePlayers, responseTimeMs);
    }
}