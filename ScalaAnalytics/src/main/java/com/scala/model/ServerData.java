package com.scala.model;

import java.util.List;
import java.util.Map;

public class ServerData {
    private final int totalPlayers;
    private final Map<String, ServerInfo> servers;
    private final long timestamp;
    private final ProxyInfo proxyInfo;

    public ServerData(int totalPlayers, Map<String, ServerInfo> servers, ProxyInfo proxyInfo) {
        this.totalPlayers = totalPlayers;
        this.servers = servers;
        this.proxyInfo = proxyInfo;
        this.timestamp = System.currentTimeMillis();
    }

    // Inner class for individual server details
    public static class ServerInfo {
        private final String name;
        private final int playerCount;
        private final List<String> players;

        public ServerInfo(String name, int playerCount, List<String> players) {
            this.name = name;
            this.playerCount = playerCount;
            this.players = players;
        }
    }

    // Inner class for proxy CPU/Memory usage
    public static class ProxyInfo {
        private final double cpuUsage; // Process CPU load
        private final long memoryUsed; // Memory used in MB

        public ProxyInfo(double cpuUsage, long memoryUsed) {
            this.cpuUsage = cpuUsage;
            this.memoryUsed = memoryUsed;
        }
    }
}
