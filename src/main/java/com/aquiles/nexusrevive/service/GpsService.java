package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.config.GpsSettings;
import com.aquiles.nexusrevive.util.Components;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GpsService {
    public static final NamespacedKey TARGET_KEY = new NamespacedKey("nexusrevive", "gps_target");
    public static final NamespacedKey ACTION_KEY = new NamespacedKey("nexusrevive", "gps_action");
    private static final NamespacedKey HOLOGRAM_KEY = new NamespacedKey("nexusrevive", "gps_hologram");

    private final NexusRevivePlugin plugin;
    private final Map<UUID, UUID> trackedTargets = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, TextDisplay> holograms = new HashMap<>();
    private final Map<UUID, Double> hologramHeights = new HashMap<>();
    private BukkitTask trackingTask;

    public GpsService(NexusRevivePlugin plugin) {
        this.plugin = plugin;
        start();
    }

    public void reload() {
        shutdown();
        start();
    }

    public void shutdown() {
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }
        for (UUID trackerId : new ArrayList<>(trackedTargets.keySet())) {
            Player tracker = Bukkit.getPlayer(trackerId);
            if (tracker != null) {
                clearVisuals(tracker);
            }
        }
        bossBars.clear();
        removeAllHolograms();
        hologramHeights.clear();
        trackedTargets.clear();
    }

    public Inventory buildMenu(Player viewer, int requestedPage) {
        GpsSettings.Gui gui = plugin.getGpsSettings().gui();
        List<Player> downedPlayers = getDownedPlayers();
        int pageSize = Math.max(1, gui.targetSlots().size());
        int totalPages = Math.max(1, (int) Math.ceil(downedPlayers.size() / (double) pageSize));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        Inventory inventory = Bukkit.createInventory(
                new GpsMenuHolder(viewer.getUniqueId(), page, totalPages),
                gui.size(),
                Components.colorize(gui.title())
        );

        Map<String, String> infoPlaceholders = menuPlaceholders(viewer, downedPlayers.size(), page, totalPages);
        renderLayout(inventory, gui, infoPlaceholders, page, totalPages);

        int startIndex = page * pageSize;
        for (int index = 0; index < gui.targetSlots().size(); index++) {
            int sourceIndex = startIndex + index;
            if (sourceIndex >= downedPlayers.size()) {
                break;
            }
            inventory.setItem(gui.targetSlots().get(index), buildTargetHead(downedPlayers.get(sourceIndex), page, totalPages));
        }

        if (downedPlayers.isEmpty()) {
            placeEmptyState(inventory, gui, infoPlaceholders);
        }
        return inventory;
    }

    public void startTracking(Player tracker, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !plugin.getDownedService().isDowned(target)) {
            plugin.getMessages().send(tracker, "gps.target-lost");
            stopTracking(tracker);
            return;
        }
        trackedTargets.put(tracker.getUniqueId(), targetId);
        plugin.getMessages().send(tracker, "gps.started", Map.of("victim", target.getName()));
        updateTracker(tracker, target);
    }

    public void toggleTracking(Player tracker, UUID targetId) {
        UUID activeTarget = trackedTargets.get(tracker.getUniqueId());
        if (activeTarget != null && activeTarget.equals(targetId)) {
            stopTracking(tracker);
            plugin.getMessages().send(tracker, "gps.stopped");
            return;
        }
        startTracking(tracker, targetId);
    }

    public void stopTracking(Player tracker) {
        trackedTargets.remove(tracker.getUniqueId());
        clearVisuals(tracker);
    }

    public void handleViewerJoin(Player viewer) {
        for (Map.Entry<UUID, TextDisplay> entry : holograms.entrySet()) {
            if (!entry.getKey().equals(viewer.getUniqueId())) {
                viewer.hideEntity(plugin, entry.getValue());
            }
        }
    }

    public List<Player> getDownedPlayers() {
        return plugin.getDownedService().getDownedPlayers().keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void start() {
        cleanupStaleHolograms();
        trackingTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tickTracking,
                plugin.getGpsSettings().updateIntervalTicks(),
                plugin.getGpsSettings().updateIntervalTicks()
        );
    }

    private void tickTracking() {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(trackedTargets.entrySet())) {
            Player tracker = Bukkit.getPlayer(entry.getKey());
            Player target = Bukkit.getPlayer(entry.getValue());
            if (tracker == null || target == null || !tracker.isOnline() || !target.isOnline() || !plugin.getDownedService().isDowned(target)) {
                if (tracker != null) {
                    plugin.getMessages().send(tracker, "gps.target-lost");
                    stopTracking(tracker);
                }
                continue;
            }

            updateTracker(tracker, target);

            if (tracker.getWorld().equals(target.getWorld())
                    && tracker.getLocation().distance(target.getLocation()) <= plugin.getGpsSettings().bossBar().arriveDistance()) {
                plugin.getMessages().send(tracker, "gps.arrived");
                stopTracking(tracker);
            }
        }
    }

    private void updateTracker(Player tracker, Player target) {
        Location targetLocation = target.getLocation();
        tracker.setCompassTarget(targetLocation);

        GpsSettings.DisplayMode mode = plugin.getGpsSettings().displayMode();
        Map<String, String> placeholders = createPlaceholders(tracker, target);

        if (mode.usesBossBar()) {
            BossBar bossBar = bossBars.computeIfAbsent(tracker.getUniqueId(), ignored -> {
                BossBar created = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                tracker.showBossBar(created);
                return created;
            });
            bossBar.name(Components.colorize(replaceFormat(plugin.getGpsSettings().bossBar().format(), placeholders)));
            bossBar.progress((float) Math.max(0.0D, Math.min(1.0D, plugin.getGpsSettings().bossBar().progress())));
        } else {
            removeBossBar(tracker);
        }

        if (mode.usesActionBar()) {
            tracker.sendActionBar(Components.colorize(replaceFormat(plugin.getGpsSettings().actionBar().format(), placeholders)));
        }

        if (mode.usesHologram()) {
            updateHologram(tracker, Components.colorize(replaceFormat(plugin.getGpsSettings().hologram().format(), placeholders)));
        } else {
            removeHologram(tracker);
        }
    }

    private Map<String, String> createPlaceholders(Player tracker, Player target) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("victim", target.getName());
        placeholders.put("arrow", resolveArrow(tracker, target.getLocation()));
        placeholders.put("pointer", plugin.getGpsSettings().hologram().pointerText());
        placeholders.put("distance", formatDistance(tracker, target.getLocation()));
        return placeholders;
    }

    private Map<String, String> menuPlaceholders(Player viewer, int targets, int page, int totalPages) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", Integer.toString(page + 1));
        placeholders.put("pages", Integer.toString(Math.max(1, totalPages)));
        placeholders.put("targets", Integer.toString(targets));
        placeholders.put("active_target", activeTargetName(viewer));
        return placeholders;
    }

    private void renderLayout(Inventory inventory, GpsSettings.Gui gui, Map<String, String> placeholders, int page, int totalPages) {
        for (int row = 0; row < gui.layout().size(); row++) {
            String line = gui.layout().get(row);
            for (int column = 0; column < 9; column++) {
                int slot = row * 9 + column;
                switch (line.charAt(column)) {
                    case '#' -> inventory.setItem(slot, buildGuiItem(gui.border(), placeholders, null));
                    case '.' -> inventory.setItem(slot, buildGuiItem(gui.filler(), placeholders, null));
                    case '+' -> inventory.setItem(slot, buildGuiItem(gui.accent(), placeholders, null));
                    case 'I' -> inventory.setItem(slot, buildGuiItem(gui.info(), placeholders, null));
                    case 'P' -> {
                        if (totalPages > 1 && page > 0) {
                            inventory.setItem(slot, buildGuiItem(gui.previous(), placeholders, "previous"));
                        }
                    }
                    case 'N' -> {
                        if (totalPages > 1 && page + 1 < totalPages) {
                            inventory.setItem(slot, buildGuiItem(gui.next(), placeholders, "next"));
                        }
                    }
                    case 'C' -> inventory.setItem(slot, buildGuiItem(gui.close(), placeholders, "close"));
                    case 'E' -> inventory.setItem(slot, buildGuiItem(gui.filler(), placeholders, null));
                    default -> {
                    }
                }
            }
        }
    }

    private void placeEmptyState(Inventory inventory, GpsSettings.Gui gui, Map<String, String> placeholders) {
        List<Integer> emptySlots = gui.emptySlots().isEmpty()
                ? List.of(gui.targetSlots().get(gui.targetSlots().size() / 2))
                : gui.emptySlots();
        for (int slot : emptySlots) {
            inventory.setItem(slot, buildGuiItem(gui.empty(), placeholders, null));
        }
    }

    private ItemStack buildGuiItem(GpsSettings.GuiItem template, Map<String, String> placeholders, String action) {
        ItemStack item = new ItemStack(template.material());
        item.editMeta(meta -> {
            meta.displayName(Components.colorize(replaceFormat(template.name(), placeholders)));
            if (!template.lore().isEmpty()) {
                meta.lore(template.lore().stream()
                        .map(line -> Components.colorize(replaceFormat(line, placeholders)))
                        .toList());
            }
            if (action != null) {
                meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
            }
        });
        return item;
    }

    private void updateHologram(Player tracker, Component text) {
        TextDisplay display = holograms.computeIfAbsent(tracker.getUniqueId(), ignored -> spawnHologram(tracker));
        UUID targetId = trackedTargets.get(tracker.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        Location location = hologramLocation(tracker, target);
        display.teleport(location);
        display.text(text);
        applyHologramScale(display);
        hideHologramFromOthers(tracker, display);
    }

    private TextDisplay spawnHologram(Player tracker) {
        TextDisplay display = tracker.getWorld().spawn(hologramLocation(tracker), TextDisplay.class, entity -> {
            entity.text(Component.empty());
            entity.setGravity(false);
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(plugin.getGpsSettings().hologram().interpolationDuration());
            entity.setTeleportDuration(Math.min(59, Math.max(0, plugin.getGpsSettings().hologram().interpolationDuration())));
            entity.setShadowed(plugin.getGpsSettings().hologram().shadowed());
            entity.setSeeThrough(plugin.getGpsSettings().hologram().seeThrough());
            entity.setDefaultBackground(plugin.getGpsSettings().hologram().defaultBackground());
            entity.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        applyHologramScale(display);
        hideHologramFromOthers(tracker, display);
        return display;
    }

    private Location hologramLocation(Player tracker) {
        return hologramLocation(tracker, null);
    }

    private Location hologramLocation(Player tracker, Player target) {
        Location base = tracker.getLocation().clone();
        base.setY(smoothedHologramY(tracker, base.getY() + 1.65D + plugin.getGpsSettings().hologram().offsetHeight()));
        Vector direction = directionToTarget(base, target)
                .multiply(plugin.getGpsSettings().hologram().distance());
        base.add(direction);
        if (target != null && target.getWorld().equals(tracker.getWorld())) {
            base.setYaw(yawToTarget(base, target.getLocation()));
        } else {
            base.setYaw(tracker.getLocation().getYaw());
        }
        base.setPitch(0.0F);
        return base;
    }

    private void hideHologramFromOthers(Player tracker, TextDisplay display) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(tracker.getUniqueId())) {
                online.showEntity(plugin, display);
            } else {
                online.hideEntity(plugin, display);
            }
        }
    }

    private void removeBossBar(Player tracker) {
        BossBar bar = bossBars.remove(tracker.getUniqueId());
        if (bar != null) {
            tracker.hideBossBar(bar);
        }
    }

    private void removeHologram(Player tracker) {
        TextDisplay display = holograms.remove(tracker.getUniqueId());
        hologramHeights.remove(tracker.getUniqueId());
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private void removeAllHolograms() {
        for (TextDisplay display : holograms.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        holograms.clear();
        hologramHeights.clear();
    }

    private void cleanupStaleHolograms() {
        for (var world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                Byte marker = display.getPersistentDataContainer().get(HOLOGRAM_KEY, PersistentDataType.BYTE);
                if (marker != null && marker == (byte) 1) {
                    display.remove();
                }
            }
        }
    }

    private void clearVisuals(Player tracker) {
        removeBossBar(tracker);
        removeHologram(tracker);
        tracker.sendActionBar(Component.empty());
    }

    private String replaceFormat(String format, Map<String, String> placeholders) {
        String result = format == null ? "" : format;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }

    private String activeTargetName(Player viewer) {
        UUID activeId = trackedTargets.get(viewer.getUniqueId());
        if (activeId == null) {
            return "ninguno";
        }
        Player target = Bukkit.getPlayer(activeId);
        return target == null ? "ninguno" : target.getName();
    }

    private String resolveArrow(Player tracker, Location targetLocation) {
        if (!tracker.getWorld().equals(targetLocation.getWorld())) {
            return "?";
        }

        double dx = targetLocation.getX() - tracker.getLocation().getX();
        double dz = targetLocation.getZ() - tracker.getLocation().getZ();
        double yawToTarget = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = normalizeDegrees(yawToTarget - tracker.getLocation().getYaw());

        return switch ((int) Math.round(diff / 45.0D) & 7) {
            case 0 -> plugin.getGpsSettings().bossBar().north();
            case 1 -> plugin.getGpsSettings().bossBar().northEast();
            case 2 -> plugin.getGpsSettings().bossBar().east();
            case 3 -> plugin.getGpsSettings().bossBar().southEast();
            case 4 -> plugin.getGpsSettings().bossBar().south();
            case 5 -> plugin.getGpsSettings().bossBar().southWest();
            case 6 -> plugin.getGpsSettings().bossBar().west();
            default -> plugin.getGpsSettings().bossBar().northWest();
        };
    }

    private Vector directionToTarget(Location from, Player target) {
        if (target == null || !from.getWorld().equals(target.getWorld())) {
            return horizontalForward(from.getYaw());
        }

        Vector delta = target.getLocation().toVector().subtract(from.toVector());
        delta.setY(0.0D);
        if (delta.lengthSquared() <= 0.0D) {
            return horizontalForward(from.getYaw());
        }
        return delta.normalize();
    }

    private Vector horizontalForward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0D, Math.cos(radians)).normalize();
    }

    private double smoothedHologramY(Player tracker, double targetY) {
        Double previous = hologramHeights.get(tracker.getUniqueId());
        if (previous == null) {
            hologramHeights.put(tracker.getUniqueId(), targetY);
            return targetY;
        }

        double factor = isGrounded(tracker) ? 0.45D : 0.12D;
        double smoothed = previous + (targetY - previous) * factor;
        hologramHeights.put(tracker.getUniqueId(), smoothed);
        return smoothed;
    }

    private boolean isGrounded(Player tracker) {
        if (tracker.isFlying() || tracker.isGliding()) {
            return false;
        }

        Location sample = tracker.getLocation().clone().subtract(0.0D, 0.15D, 0.0D);
        return !sample.getBlock().isPassable();
    }

    private void applyHologramScale(TextDisplay display) {
        float scale = plugin.getGpsSettings().hologram().scale();
        display.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        ));
    }

    private float yawToTarget(Location from, Location target) {
        Vector delta = target.toVector().subtract(from.toVector());
        delta.setY(0.0D);
        if (delta.lengthSquared() <= 0.0D) {
            return from.getYaw();
        }
        return (float) Math.toDegrees(Math.atan2(-delta.getX(), delta.getZ()));
    }

    private String formatDistance(Player tracker, Location targetLocation) {
        if (!tracker.getWorld().equals(targetLocation.getWorld())) {
            return plugin.getGpsSettings().bossBar().otherWorldText();
        }
        return (int) Math.round(tracker.getLocation().distance(targetLocation)) + "m";
    }

    private double normalizeDegrees(double degrees) {
        while (degrees > 180.0D) {
            degrees -= 360.0D;
        }
        while (degrees < -180.0D) {
            degrees += 360.0D;
        }
        return degrees;
    }

    private ItemStack buildTargetHead(Player target, int page, int totalPages) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(meta -> {
            if (meta instanceof SkullMeta skullMeta) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("victim", target.getName());
                placeholders.put("world", target.getWorld().getName());
                placeholders.put("x", Integer.toString(target.getLocation().getBlockX()));
                placeholders.put("y", Integer.toString(target.getLocation().getBlockY()));
                placeholders.put("z", Integer.toString(target.getLocation().getBlockZ()));
                placeholders.put("page", Integer.toString(page + 1));
                placeholders.put("pages", Integer.toString(Math.max(1, totalPages)));
                OfflinePlayer owner = Bukkit.getOfflinePlayer(target.getUniqueId());
                skullMeta.setOwningPlayer(owner);
                skullMeta.displayName(Components.colorize(replaceFormat(plugin.getGpsSettings().gui().target().name(), placeholders)));
                if (!plugin.getGpsSettings().gui().target().lore().isEmpty()) {
                    skullMeta.lore(plugin.getGpsSettings().gui().target().lore().stream()
                            .map(line -> Components.colorize(replaceFormat(line, placeholders)))
                            .toList());
                }
                skullMeta.getPersistentDataContainer().set(TARGET_KEY, PersistentDataType.STRING, target.getUniqueId().toString());
            }
        });
        return head;
    }

    public record GpsMenuHolder(UUID viewerId, int page, int totalPages) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
