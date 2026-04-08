package com.aquiles.nexusrevive;

import com.aquiles.nexusrevive.placeholder.NexusRevivePlaceholderExpansion;
import com.aquiles.nexusrevive.command.NexusReviveCommand;
import com.aquiles.nexusrevive.config.GpsSettings;
import com.aquiles.nexusrevive.config.Messages;
import com.aquiles.nexusrevive.config.PluginSettings;
import com.aquiles.nexusrevive.util.Components;
import com.aquiles.nexusrevive.listener.CombatListener;
import com.aquiles.nexusrevive.listener.GpsMenuListener;
import com.aquiles.nexusrevive.listener.LootListener;
import com.aquiles.nexusrevive.listener.PlayerStateListener;
import com.aquiles.nexusrevive.listener.ZoneSelectionListener;
import com.aquiles.nexusrevive.lib.bstats.bukkit.Metrics;
import com.aquiles.nexusrevive.nms.DownedPoseAdapter;
import com.aquiles.nexusrevive.nms.NmsAdapterResolver;
import com.aquiles.nexusrevive.scheduler.NexusScheduler;
import com.aquiles.nexusrevive.service.CompatibilityService;
import com.aquiles.nexusrevive.service.DownedService;
import com.aquiles.nexusrevive.service.EventActionsService;
import com.aquiles.nexusrevive.service.GpsService;
import com.aquiles.nexusrevive.service.LootService;
import com.aquiles.nexusrevive.service.NmsPoseBroadcaster;
import com.aquiles.nexusrevive.service.ScoreboardService;
import com.aquiles.nexusrevive.service.VulcanHookService;
import com.aquiles.nexusrevive.service.ZoneService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class NexusRevivePlugin extends JavaPlugin {
    private static final String UPDATE_CHECK_URL = "https://gist.githubusercontent.com/zlAquiles/1924d746bfbb0a608af9485c0aa029f6/raw/version.txt";
    private static final String STARTUP_ART = """
             ____            _
            |  _ \\ _____   _(_)_   _____
            | |_) / _ \\ \\ / / \\ \\ / / _ \\
            |  _ <  __/\\ V /| |\\ V /  __/
            |_| \\_\\___| \\_/ |_| \\_/ \\___|
            """;
    private static NexusRevivePlugin instance;

    private PluginSettings pluginSettings;
    private Messages messages;
    private GpsSettings gpsSettings;
    private ZoneService zoneService;
    private DownedService downedService;
    private GpsService gpsService;
    private LootService lootService;
    private ScoreboardService scoreboardService;
    private CompatibilityService compatibilityService;
    private VulcanHookService vulcanHookService;
    private EventActionsService eventActionsService;
    private NmsPoseBroadcaster nmsPoseBroadcaster;
    private DownedPoseAdapter downedPoseAdapter;
    private NexusScheduler scheduler;
    private NexusRevivePlaceholderExpansion placeholderExpansion;
    private boolean shuttingDown;

    @Override
    public void onLoad() {
        CompatibilityService.registerWorldGuardFlagEarly(this);
    }

    @Override
    public void onEnable() {
        long startNanos = System.nanoTime();
        instance = this;
        shuttingDown = false;
        String minecraftVersion = getServer().getMinecraftVersion();
        if (compareVersions(minecraftVersion, "1.19.4") < 0) {
            getLogger().severe("NexusRevive requiere al menos Minecraft 1.19.4. Version detectada: " + minecraftVersion);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ensureRuntimeResources();
        this.scheduler = new NexusScheduler(this);
        this.nmsPoseBroadcaster = new NmsPoseBroadcaster(this);
        this.downedPoseAdapter = NmsAdapterResolver.resolve(this, minecraftVersion, nmsPoseBroadcaster);

        reloadPlugin();

        NexusReviveCommand commandExecutor = new NexusReviveCommand(this);
        PluginCommand command = getCommand("nexusrevive");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerStateListener(this), this);
        getServer().getPluginManager().registerEvents(new GpsMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new LootListener(this), this);
        getServer().getPluginManager().registerEvents(new ZoneSelectionListener(this), this);

        this.vulcanHookService = new VulcanHookService(this);
        this.vulcanHookService.registerIfAvailable();

        registerPlaceholderExpansion();
        setupMetrics();
        logStartupBanner(startNanos);
        checkForUpdatesAsync();
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (gpsService != null) {
            gpsService.shutdown();
        }
        if (lootService != null) {
            lootService.shutdown();
        }
        if (scoreboardService != null) {
            scoreboardService.shutdown();
        }
        if (zoneService != null) {
            zoneService.shutdown();
        }
        if (downedService != null) {
            downedService.shutdown();
        }
        instance = null;
    }

    public void reloadPlugin() {
        ensureRuntimeResources();
        reloadConfig();
        FileConfiguration config = getConfig();

        this.pluginSettings = PluginSettings.from(config);
        this.messages = new Messages(loadYaml("messages.yml"));
        this.gpsSettings = GpsSettings.from(loadYaml("gps.yml"));

        if (this.compatibilityService == null) {
            this.compatibilityService = new CompatibilityService(this);
        } else {
            this.compatibilityService.reload();
        }

        if (this.eventActionsService == null) {
            this.eventActionsService = new EventActionsService(this);
        } else {
            this.eventActionsService.reload();
        }

        YamlConfiguration zonesConfig = loadYaml("zones.yml");
        if (this.zoneService == null) {
            this.zoneService = new ZoneService(this, zonesConfig);
        } else {
            this.zoneService.reload(zonesConfig);
        }

        if (this.downedService == null) {
            this.downedService = new DownedService(this);
        } else {
            this.downedService.reload();
        }

        if (this.gpsService == null) {
            this.gpsService = new GpsService(this);
        } else {
            this.gpsService.reload();
        }

        if (this.lootService == null) {
            this.lootService = new LootService(this);
        } else {
            this.lootService.reload();
        }

        if (this.scoreboardService == null) {
            this.scoreboardService = new ScoreboardService(this);
        } else {
            this.scoreboardService.reload();
        }
    }

    public PluginSettings getPluginSettings() {
        return pluginSettings;
    }

    public static NexusRevivePlugin getInstance() {
        return instance;
    }

    public Messages getMessages() {
        return messages;
    }

    public GpsSettings getGpsSettings() {
        return gpsSettings;
    }

    public ZoneService getZoneService() {
        return zoneService;
    }

    public DownedService getDownedService() {
        return downedService;
    }

    public GpsService getGpsService() {
        return gpsService;
    }

    public LootService getLootService() {
        return lootService;
    }

    public ScoreboardService getScoreboardService() {
        return scoreboardService;
    }

    public CompatibilityService getCompatibilityService() {
        return compatibilityService;
    }

    public EventActionsService getEventActionsService() {
        return eventActionsService;
    }

    public NmsPoseBroadcaster getNmsPoseBroadcaster() {
        return nmsPoseBroadcaster;
    }

    public DownedPoseAdapter getDownedPoseAdapter() {
        return downedPoseAdapter;
    }

    public NexusScheduler getSchedulerFacade() {
        return scheduler;
    }

    public boolean isServerStopping() {
        if (shuttingDown) {
            return true;
        }

        try {
            Method method = getServer().getClass().getMethod("isStopping");
            Object result = method.invoke(getServer());
            return result instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public void saveZones() {
        try {
            zoneService.getConfig().save(new File(getDataFolder(), "zones.yml"));
        } catch (IOException exception) {
            getLogger().warning("No se pudo guardar zones.yml: " + exception.getMessage());
        }
    }

    private YamlConfiguration loadYaml(String name) {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), name));
    }

    private void ensureRuntimeResources() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("gps.yml");
        saveResourceIfMissing("zones.yml");
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    private void registerPlaceholderExpansion() {
        if (!isHookEnabled("PlaceholderAPI")) {
            return;
        }
        if (placeholderExpansion == null) {
            placeholderExpansion = new NexusRevivePlaceholderExpansion(this);
        }
        placeholderExpansion.register();
    }

    private void setupMetrics() {
        try {
            new Metrics(this, 30328);
        } catch (Throwable error) {
            getLogger().warning("No se pudo iniciar bStats: " + error.getMessage());
        }
    }

    private void logStartupBanner(long startNanos) {
        long tookMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        String version = pluginVersion();

        getServer().getConsoleSender().sendMessage(" ");
        String[] artLines = STARTUP_ART.stripIndent().split("\\R");
        getServer().getConsoleSender().sendMessage(Components.colorize("&#55d7ff" + artLines[0]));
        getServer().getConsoleSender().sendMessage(Components.colorize("&#55d7ff" + artLines[1] + " &fRevive &7v" + version));
        getServer().getConsoleSender().sendMessage(Components.colorize("&#55d7ff" + artLines[2] + " &7Running on &f" + getServer().getName()));
        getServer().getConsoleSender().sendMessage(Components.colorize("&#55d7ff" + artLines[3] + " &7By &fAquiles"));
        getServer().getConsoleSender().sendMessage(Components.colorize("&#55d7ff" + artLines[4]));
        getServer().getConsoleSender().sendMessage(" ");
        getServer().getConsoleSender().sendMessage(Components.colorize("&aSuccessfully enabled.&7 (took " + tookMs + "ms)"));
    }

    private void checkForUpdatesAsync() {
        if (!pluginSettings.updater().enabled()) {
            return;
        }

        scheduler.runAsync(() -> {
            try {
                String latestVersion = fetchLatestVersion();
                scheduler.runGlobal(() -> logUpdateResult(latestVersion));
            } catch (Exception exception) {
                scheduler.runGlobal(() -> logUpdateFailure(exception));
            }
        });
    }

    private String fetchLatestVersion() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(UPDATE_CHECK_URL).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setRequestProperty("User-Agent", "NexusRevive-Updater");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String version = reader.readLine();
            if (version == null) {
                throw new IOException("La respuesta del updater esta vacia.");
            }
            return version.trim();
        } finally {
            connection.disconnect();
        }
    }

    private void logUpdateResult(String latestVersion) {
        if (latestVersion.isBlank()) {
            return;
        }

        if (compareVersions(pluginVersion(), latestVersion) < 0) {
            getServer().getConsoleSender().sendMessage(Components.colorize(consolePrefix()
                    + "&eUpdate available: "
                    + "&f" + latestVersion
                    + "&7 (current "
                    + "&f" + pluginVersion()
                    + "&7)"));
        } else {
            getServer().getConsoleSender().sendMessage(Components.colorize(consolePrefix() + "&aYou are running the latest version!"));
        }
    }

    private void logUpdateFailure(Exception exception) {
        getServer().getConsoleSender().sendMessage(Components.colorize(consolePrefix()
                + "&eCould not check for updates"
                + "&7: " + exception.getMessage()));
    }

    private boolean isHookEnabled(String pluginName) {
        return getServer().getPluginManager().isPluginEnabled(pluginName);
    }

    private String consolePrefix() {
        return "&8[&bNexusRevive&8] ";
    }

    private String pluginVersion() {
        return getPluginMeta().getVersion();
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private int parseVersionPart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}

