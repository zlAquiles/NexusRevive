package com.aquiles.nexusrevive.lib.bstats.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

public final class Metrics {
    private static final String REPORT_URL = "https://bStats.org/api/v2/data/bukkit";
    private static final long INITIAL_DELAY_TICKS = 20L * 60L * 5L;
    private static final long SUBMIT_PERIOD_TICKS = 20L * 60L * 30L;

    private final Plugin plugin;
    private final int serviceId;
    private final boolean enabled;
    private final boolean logFailedRequests;
    private final boolean logSentData;
    private final boolean logResponseStatusText;
    private final String serverUuid;
    private BukkitTask submitTask;

    public Metrics(Plugin plugin, int serviceId) {
        this.plugin = plugin;
        this.serviceId = serviceId;
        this.enabled = true;
        this.logFailedRequests = false;
        this.logSentData = false;
        this.logResponseStatusText = false;
        this.serverUuid = UUID.nameUUIDFromBytes(
                (plugin.getServer().getWorldContainer().getAbsolutePath()
                        + '|'
                        + plugin.getServer().getPort()
                        + '|'
                        + plugin.getPluginMeta().getName())
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();

        if (enabled) {
            startSubmitting();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        if (submitTask != null) {
            submitTask.cancel();
            submitTask = null;
        }
    }

    private void startSubmitting() {
        submitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!plugin.isEnabled()) {
                shutdown();
                return;
            }

            try {
                submitData();
            } catch (Throwable throwable) {
                if (logFailedRequests) {
                    plugin.getLogger().log(Level.WARNING, "No se pudo enviar metrics a bStats.", throwable);
                }
            }
        }, INITIAL_DELAY_TICKS, SUBMIT_PERIOD_TICKS);
    }

    private void submitData() throws IOException {
        String payload = buildPayload();
        if (payload.isBlank()) {
            return;
        }

        if (logSentData) {
            plugin.getLogger().info("Sent bStats metrics data: " + payload);
        }

        byte[] compressed = compress(payload);
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(REPORT_URL).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("Connection", "close");
        connection.addRequestProperty("Content-Encoding", "gzip");
        connection.addRequestProperty("Content-Length", String.valueOf(compressed.length));
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("User-Agent", "NexusRevive-bStats");
        connection.setDoOutput(true);

        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            outputStream.write(compressed);
        }

        if (logResponseStatusText) {
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            plugin.getLogger().info("Sent data to bStats and received response: " + response);
        } else {
            connection.getResponseCode();
        }
        connection.disconnect();
    }

    private String buildPayload() {
        return "{"
                + "\"serverUUID\":\"" + escape(serverUuid) + "\","
                + "\"playerAmount\":" + Bukkit.getOnlinePlayers().size() + ','
                + "\"onlineMode\":" + (Bukkit.getOnlineMode() ? 1 : 0) + ','
                + "\"bukkitVersion\":\"" + escape(Bukkit.getVersion()) + "\","
                + "\"bukkitName\":\"" + escape(Bukkit.getName()) + "\","
                + "\"javaVersion\":\"" + escape(System.getProperty("java.version", "unknown")) + "\","
                + "\"osName\":\"" + escape(System.getProperty("os.name", "unknown")) + "\","
                + "\"osArch\":\"" + escape(System.getProperty("os.arch", "unknown")) + "\","
                + "\"osVersion\":\"" + escape(System.getProperty("os.version", "unknown")) + "\","
                + "\"coreCount\":" + Runtime.getRuntime().availableProcessors() + ','
                + "\"service\":{"
                + "\"id\":" + serviceId + ','
                + "\"name\":\"" + escape(plugin.getPluginMeta().getName()) + "\","
                + "\"pluginVersion\":\"" + escape(plugin.getPluginMeta().getVersion()) + "\","
                + "\"customCharts\":[]"
                + "}"
                + "}";
    }

    private static byte[] compress(String payload) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        return outputStream.toByteArray();
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}

