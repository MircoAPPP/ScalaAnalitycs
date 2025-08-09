package com.scala;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.moandjiezana.toml.Toml;


@Plugin(
    id = "scalaanalytics",
    name = "ScalaAnalytics",
    version = "1.0.0",
    description = "Sends server analytics to a web dashboard.",
    authors = {"Jules"}
)
public class ScalaAnalytics {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private AnalyticsTask analyticsTask;
    private ScheduledExecutorService scheduler;
    private Toml config;

    @Inject
    public ScalaAnalytics(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Load config
        if (!loadConfig()) {
            logger.error("Failed to load or create config.toml. Plugin will not start.");
            return;
        }

        logger.info("ScalaAnalytics is starting!");

        String websocketUrl = config.getString("websocket-url");
        String secretKey = config.getString("secret-key");

        if (websocketUrl == null || websocketUrl.isEmpty() || secretKey == null || secretKey.isEmpty() || secretKey.equals("change-me-to-a-secure-secret")) {
            logger.error("WebSocket URL or Secret Key is not configured in config.toml. Please configure it and restart the proxy.");
            return;
        }

        try {
            URI serverUri = new URI(websocketUrl);
            analyticsTask = new AnalyticsTask(this.server, new Gson(), serverUri, secretKey);
            analyticsTask.connect();

            long updateInterval = config.getLong("update-interval", 10L);

            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                if(analyticsTask.isOpen()){
                    analyticsTask.sendAnalytics();
                } else {
                    logger.warn("WebSocket is not connected. Trying to reconnect...");
                    try {
                        analyticsTask.reconnectBlocking();
                    } catch (InterruptedException | URISyntaxException e) {
                        logger.error("Failed to reconnect WebSocket", e);
                    }
                }
            }, 0, updateInterval, TimeUnit.SECONDS);

            logger.info("Successfully started ScalaAnalytics and connected to WebSocket.");

        } catch (URISyntaxException e) {
            logger.error("Invalid WebSocket URL in config.toml", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Shutting down ScalaAnalytics...");
        if (analyticsTask != null && analyticsTask.isOpen()) {
            analyticsTask.close();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        logger.info("ScalaAnalytics has been shut down.");
    }

    private boolean loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configFile = dataDirectory.resolve("config.toml");
            if (!Files.exists(configFile)) {
                logger.info("Creating default config.toml...");
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.toml")) {
                    if (in == null) {
                        logger.error("Default config.toml not found in plugin JAR.");
                        return false;
                    }
                    Files.copy(in, configFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            config = new Toml().read(configFile.toFile());
            return true;
        } catch (IOException e) {
            logger.error("Error creating or loading config file", e);
            return false;
        }
    }
}
