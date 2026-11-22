package com.quizbattle.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Announcement payload broadcasted over UDP port 5001 for room discovery.
 */
public class DiscoveryBeacon {

    private final String serverId;
    private final String serverHost;
    private final int serverTcpPort;
    private final String roomName;
    private final int activePlayers;
    private final int maxPlayers;

    @JsonCreator
    public DiscoveryBeacon(
            @JsonProperty("serverId") String serverId,
            @JsonProperty("serverHost") String serverHost,
            @JsonProperty("serverTcpPort") int serverTcpPort,
            @JsonProperty("roomName") String roomName,
            @JsonProperty("activePlayers") int activePlayers,
            @JsonProperty("maxPlayers") int maxPlayers) {
        this.serverId = Objects.requireNonNull(serverId, "serverId must not be null");
        this.serverHost = Objects.requireNonNull(serverHost, "serverHost must not be null");
        this.serverTcpPort = serverTcpPort;
        this.roomName = roomName;
        this.activePlayers = activePlayers;
        this.maxPlayers = maxPlayers;
    }

    public String getServerId() {
        return serverId;
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerTcpPort() {
        return serverTcpPort;
    }

    public String getRoomName() {
        return roomName;
    }

    public int getActivePlayers() {
        return activePlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveryBeacon that)) return false;
        return serverTcpPort == that.serverTcpPort &&
                Objects.equals(serverId, that.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, serverTcpPort);
    }

    @Override
    public String toString() {
        return "DiscoveryBeacon{" +
                "serverId='" + serverId + '\'' +
                ", serverHost='" + serverHost + '\'' +
                ", serverTcpPort=" + serverTcpPort +
                ", roomName='" + roomName + '\'' +
                ", players=" + activePlayers + "/" + maxPlayers +
                '}';
    }
}
