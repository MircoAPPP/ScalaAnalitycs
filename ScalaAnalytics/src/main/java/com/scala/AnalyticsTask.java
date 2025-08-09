package com.scala;

import com.google.gson.Gson;
import com.scala.model.ServerData;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnalyticsTask extends WebSocketClient {

    private final ProxyServer proxyServer;
    private final Gson gson;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AnalyticsTask.class);
    private final OperatingSystemMXBean osBean;


    public AnalyticsTask(ProxyServer proxyServer, Gson gson, URI serverUri, String secretKey) throws URISyntaxException {
        super(serverUri, createHeaders(secretKey));
        this.proxyServer = proxyServer;
        this.gson = gson;
        this.osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    }

    private static Map<String, String> createHeaders(String secretKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Secret-Key", secretKey);
        return headers;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("Connected to ScalaAnalytics backend at " + getURI());
        sendAnalytics(); // Send initial data on connect
    }

    @Override
    public void onMessage(String message) {
        // We don't expect messages from the server, but we can log them.
        logger.info("Received message from backend: " + message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("Disconnected from ScalaAnalytics backend. Reason: " + reason + " (Code: " + code + ")");
    }

    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error", ex);
    }

    public void sendAnalytics() {
        if (!isOpen()) {
            logger.warn("Cannot send analytics, WebSocket is not open.");
            return;
        }

        Map<String, ServerData.ServerInfo> serverInfoMap = new HashMap<>();
        for (RegisteredServer server : proxyServer.getAllServers()) {
            List<String> playerUsernames = server.getPlayersConnected().stream()
                    .map(Player::getUsername)
                    .collect(Collectors.toList());

            ServerData.ServerInfo info = new ServerData.ServerInfo(
                    server.getServerInfo().getName(),
                    server.getPlayersConnected().size(),
                    playerUsernames
            );
            serverInfoMap.put(server.getServerInfo().getName(), info);
        }

        // Get Proxy Info
        double processCpuLoad = osBean.getProcessCpuLoad() * 100;
        long memoryUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        ServerData.ProxyInfo proxyInfo = new ServerData.ProxyInfo(processCpuLoad, memoryUsed);

        ServerData payload = new ServerData(
                proxyServer.getPlayerCount(),
                serverInfoMap,
                proxyInfo
        );

        String jsonPayload = gson.toJson(payload);
        send(jsonPayload);
        logger.info("Analytics data sent.");
    }
}
