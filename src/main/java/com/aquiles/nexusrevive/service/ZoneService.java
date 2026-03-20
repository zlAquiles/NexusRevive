package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.model.ReviveZone;
import com.aquiles.nexusrevive.model.Selection;
import com.aquiles.nexusrevive.util.Components;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ZoneService {
    private static final Color TRANSPARENT = Color.fromARGB(0, 0, 0, 0);
    private static final Color POS1_COLOR = Color.fromARGB(0x66, 0xFF, 0x55, 0x55);
    private static final Color POS2_COLOR = Color.fromARGB(0x66, 0x33, 0xFF, 0xFF);
    private static final Color PREVIEW_COLOR = Color.fromRGB(0x55, 0xFF, 0xFF);
    private static final double FACE_INSET = -0.004D;
    private static final int FACE_TEXT_WIDTH = 10;
    private static final int FACE_TEXT_LINES = 4;
    private static final float POS_DISPLAY_WIDTH = 0.75F;
    private static final float POS_DISPLAY_HEIGHT = 0.28F;
    private static final int POS_LINE_WIDTH = 160;
    private static final boolean POS_SHADOWED = true;
    private static final float COORDS_DISPLAY_WIDTH = 0.9F;
    private static final float COORDS_DISPLAY_HEIGHT = 0.3F;
    private static final int COORDS_LINE_WIDTH = 180;
    private static final long PREVIEW_INTERVAL_TICKS = 6L;
    private static final int PREVIEW_EDGE_POINTS = 18;
    private static final String POS1_TEXT = "&cPOS 1";
    private static final String POS2_TEXT = "&bPOS 2";
    private static final String COORDS_FORMAT = "&7<x>, <y>, <z>";
    private static final Material WAND_MATERIAL = Material.GOLDEN_AXE;

    private final NexusRevivePlugin plugin;
    private final Map<String, ReviveZone> zones = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, SelectionMarkers> holograms = new HashMap<>();
    private final NamespacedKey wandKey;
    private YamlConfiguration config;
    private BukkitTask previewTask;

    public ZoneService(NexusRevivePlugin plugin, YamlConfiguration config) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "zone_wand");
        reload(config);
    }

    public void reload(YamlConfiguration config) {
        this.config = config;
        zones.clear();

        ConfigurationSection section = config.getConfigurationSection("zones");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection zone = section.getConfigurationSection(key);
                if (zone == null) {
                    continue;
                }
                zones.put(key.toLowerCase(), new ReviveZone(
                        key,
                        zone.getString("world", "world"),
                        zone.getInt("min.x"),
                        zone.getInt("min.y"),
                        zone.getInt("min.z"),
                        zone.getInt("max.x"),
                        zone.getInt("max.y"),
                        zone.getInt("max.z"),
                        zone.getDouble("speed-multiplier", 1.0D),
                        zone.getBoolean("auto-revive", true)
                ));
            }
        }

        restartPreviewTask();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            hideMarkersFrom(viewer);
        }
        for (Player owner : Bukkit.getOnlinePlayers()) {
            Selection selection = selections.get(owner.getUniqueId());
            if (selection != null && selection.isSelectorEnabled()) {
                updateDisplays(owner);
            }
        }
    }

    public void shutdown() {
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }
        for (UUID ownerId : new ArrayList<>(holograms.keySet())) {
            removeMarkers(ownerId);
        }
    }

    public Optional<ReviveZone> getZoneAt(Location location) {
        return zones.values().stream().filter(zone -> zone.contains(location)).findFirst();
    }

    public double getSpeedMultiplier(Location location) {
        return getZoneAt(location).map(ReviveZone::speedMultiplier).orElse(1.0D);
    }

    public Selection selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
    }

    public void giveWand(Player player) {
        Selection selection = selection(player);
        selection.setSelectorEnabled(true);
        selection.touch();
        ItemStack wand = createWand();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(wand);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    public boolean isWand(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(wandKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public void setPosition(Player player, boolean first, Location location) {
        Location snapped = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Selection selection = selection(player);
        selection.setSelectorEnabled(true);
        if (first) {
            selection.setPos1(snapped);
        } else {
            selection.setPos2(snapped);
        }
        selection.touch();
        updateDisplays(player);
    }

    public boolean hasCompleteSelection(Player player) {
        return selection(player).isComplete();
    }

    public boolean hasWorldMismatch(Player player) {
        return selection(player).hasWorldMismatch();
    }

    public boolean createZone(String name, Player player, double speedMultiplier) {
        Selection selection = selection(player);
        Location pos1 = selection.getPos1();
        Location pos2 = selection.getPos2();
        if (zones.containsKey(name.toLowerCase())
                || pos1 == null || pos2 == null
                || pos1.getWorld() == null || pos2.getWorld() == null
                || !pos1.getWorld().equals(pos2.getWorld())) {
            return false;
        }

        ReviveZone zone = new ReviveZone(
                name,
                pos1.getWorld().getName(),
                Math.min(pos1.getBlockX(), pos2.getBlockX()),
                Math.min(pos1.getBlockY(), pos2.getBlockY()),
                Math.min(pos1.getBlockZ(), pos2.getBlockZ()),
                Math.max(pos1.getBlockX(), pos2.getBlockX()),
                Math.max(pos1.getBlockY(), pos2.getBlockY()),
                Math.max(pos1.getBlockZ(), pos2.getBlockZ()),
                speedMultiplier,
                true
        );
        zones.put(name.toLowerCase(), zone);
        writeZone(zone);
        plugin.saveZones();
        selection.clear();
        updateDisplays(player);
        return true;
    }

    public boolean removeZone(String name) {
        ReviveZone removed = zones.remove(name.toLowerCase());
        if (removed == null) {
            return false;
        }
        config.set("zones." + removed.name(), null);
        plugin.saveZones();
        return true;
    }

    public boolean updateSpeed(String name, double speedMultiplier) {
        ReviveZone zone = zones.get(name.toLowerCase());
        if (zone == null) {
            return false;
        }
        ReviveZone updated = new ReviveZone(zone.name(), zone.worldName(), zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ(), speedMultiplier, zone.autoRevive());
        zones.put(name.toLowerCase(), updated);
        writeZone(updated);
        plugin.saveZones();
        return true;
    }

    public Collection<ReviveZone> getZones() {
        return zones.values().stream().sorted(Comparator.comparing(ReviveZone::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Optional<ReviveZone> getAutoReviveZone(Location location) {
        return getZoneAt(location).filter(ReviveZone::autoRevive);
    }

    public void hideMarkersFrom(Player viewer) {
        for (SelectionMarkers markers : holograms.values()) {
            hideGroup(viewer, markers.pos1);
            hideGroup(viewer, markers.pos2);
        }
    }

    public void clear(Player player) {
        selections.remove(player.getUniqueId());
        removeMarkers(player.getUniqueId());
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    private void restartPreviewTask() {
        if (previewTask != null) {
            previewTask.cancel();
        }
        previewTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderSelections, PREVIEW_INTERVAL_TICKS, PREVIEW_INTERVAL_TICKS);
    }

    private void renderSelections() {
        for (Player owner : Bukkit.getOnlinePlayers()) {
            Selection selection = selections.get(owner.getUniqueId());
            if (selection == null || !selection.isSelectorEnabled()) {
                continue;
            }
            if (selection.getPos1() != null) {
                renderMarker(owner, selection.getPos1(), Color.RED);
            }
            if (selection.getPos2() != null) {
                renderMarker(owner, selection.getPos2(), Color.AQUA);
            }
            if (selection.isComplete() && !selection.hasWorldMismatch()) {
                renderBox(owner, selection.getPos1(), selection.getPos2());
            }
        }
    }

    private void updateDisplays(Player owner) {
        Selection selection = selections.get(owner.getUniqueId());
        if (selection == null || !selection.isSelectorEnabled()) {
            removeMarkers(owner.getUniqueId());
            return;
        }

        SelectionMarkers markers = holograms.computeIfAbsent(owner.getUniqueId(), ignored -> new SelectionMarkers());
        markers.pos1 = updateMarkerGroup(owner, markers.pos1, selection.getPos1(), true);
        markers.pos2 = updateMarkerGroup(owner, markers.pos2, selection.getPos2(), false);
    }

    private MarkerGroup updateMarkerGroup(Player owner, MarkerGroup group, Location block, boolean first) {
        if (block == null || block.getWorld() == null) {
            removeGroup(group);
            return new MarkerGroup();
        }

        MarkerGroup result = group == null ? new MarkerGroup() : group;
        String labelText = first ? POS1_TEXT : POS2_TEXT;
        String coordText = COORDS_FORMAT
                .replace("<x>", Integer.toString(block.getBlockX()))
                .replace("<y>", Integer.toString(block.getBlockY()))
                .replace("<z>", Integer.toString(block.getBlockZ()));
        String panelText = blankPanelText();
        Color background = first ? POS1_COLOR : POS2_COLOR;

        for (Face face : Face.values()) {
            int index = face.ordinal();
            Location panelLocation = face.panelLocation(block);
            FaceSettings settings = face.settings();
            result.panels[index] = upsertDisplay(owner, result.panels[index], panelLocation, panelText, background, Display.Billboard.FIXED, face.yaw, face.pitch,
                    settings.displayWidth, settings.displayHeight, settings.lineWidth, settings.scaleX, settings.scaleY, settings.scaleZ, false, false, (byte) 127, true);

            if (face == Face.TOP) {
                removeDisplay(result.labels[index]);
                result.labels[index] = null;
                continue;
            }
            Location labelLocation = face.labelLocation(panelLocation);
            result.labels[index] = upsertDisplay(owner, result.labels[index], labelLocation, labelText, TRANSPARENT, Display.Billboard.FIXED, face.yaw, face.pitch,
                    POS_DISPLAY_WIDTH, POS_DISPLAY_HEIGHT, POS_LINE_WIDTH, 1.0F, 1.0F, 1.0F, POS_SHADOWED, false, (byte) 127, true);
        }

        Location coordsLocation = block.clone().add(0.5D, 1.1D, 0.5D);
        result.coords = upsertDisplay(owner, result.coords, coordsLocation, coordText, TRANSPARENT, Display.Billboard.CENTER, 0.0F, 0.0F,
                COORDS_DISPLAY_WIDTH, COORDS_DISPLAY_HEIGHT, COORDS_LINE_WIDTH, 0.5F, 0.5F, 0.5F, true, true, (byte) 190, true);
        return result;
    }

    private TextDisplay upsertDisplay(Player owner, TextDisplay current, Location location, String text, Color background,
                                      Display.Billboard billboard, float yaw, float pitch,
                                      float displayWidth, float displayHeight, int lineWidth,
                                      float scaleX, float scaleY, float scaleZ,
                                      boolean shadowed, boolean seeThrough, byte opacity, boolean fixedBrightness) {
        TextDisplay display = current;
        if (display == null || !display.isValid() || display.getWorld() != location.getWorld()) {
            removeDisplay(display);
            display = location.getWorld().spawn(location, TextDisplay.class);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setSilent(true);
            display.setGravity(false);
            display.setDefaultBackground(false);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
        } else {
            display.teleport(location);
        }

        display.setBillboard(billboard);
        display.setRotation(yaw, pitch);
        display.text(Components.colorize(text));
        display.setBackgroundColor(background);
        display.setDisplayWidth(displayWidth);
        display.setDisplayHeight(displayHeight);
        display.setLineWidth(lineWidth);
        display.setShadowed(shadowed);
        display.setSeeThrough(seeThrough);
        display.setTextOpacity(opacity);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scaleX, scaleY, scaleZ), new Quaternionf()));
        display.setBrightness(fixedBrightness ? new Display.Brightness(15, 15) : null);
        refreshVisibility(owner, display);
        return display;
    }

    private void refreshVisibility(Player owner, TextDisplay display) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(owner.getUniqueId())) {
                online.showEntity(plugin, display);
            } else {
                online.hideEntity(plugin, display);
            }
        }
    }

    private void hideGroup(Player viewer, MarkerGroup group) {
        if (group == null) {
            return;
        }
        for (TextDisplay panel : group.panels) {
            hideFrom(viewer, panel);
        }
        for (TextDisplay label : group.labels) {
            hideFrom(viewer, label);
        }
        hideFrom(viewer, group.coords);
    }

    private void hideFrom(Player viewer, TextDisplay display) {
        if (display != null && display.isValid()) {
            viewer.hideEntity(plugin, display);
        }
    }

    private void removeMarkers(UUID ownerId) {
        SelectionMarkers removed = holograms.remove(ownerId);
        if (removed == null) {
            return;
        }
        removeGroup(removed.pos1);
        removeGroup(removed.pos2);
    }

    private void removeGroup(MarkerGroup group) {
        if (group == null) {
            return;
        }
        for (TextDisplay panel : group.panels) {
            removeDisplay(panel);
        }
        for (TextDisplay label : group.labels) {
            removeDisplay(label);
        }
        removeDisplay(group.coords);
    }

    private void removeDisplay(TextDisplay display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private void renderMarker(Player player, Location location, Color color) {
        player.spawnParticle(resolveDustParticle(), location.getBlockX() + 0.5D, location.getBlockY() + 1.1D, location.getBlockZ() + 0.5D,
                6, 0.18D, 0.18D, 0.18D, 0.0D, new Particle.DustOptions(color, 1.2F));
    }

    private void renderBox(Player player, Location first, Location second) {
        if (first.getWorld() == null || !first.getWorld().equals(player.getWorld())) {
            return;
        }
        Particle.DustOptions options = new Particle.DustOptions(PREVIEW_COLOR, 0.9F);
        int samplesPerEdge = Math.max(2, Math.max(12, PREVIEW_EDGE_POINTS) / 12);
        double minX = Math.min(first.getBlockX(), second.getBlockX()) + 0.5D;
        double minY = Math.min(first.getBlockY(), second.getBlockY()) + 0.5D;
        double minZ = Math.min(first.getBlockZ(), second.getBlockZ()) + 0.5D;
        double maxX = Math.max(first.getBlockX(), second.getBlockX()) + 0.5D;
        double maxY = Math.max(first.getBlockY(), second.getBlockY()) + 0.5D;
        double maxZ = Math.max(first.getBlockZ(), second.getBlockZ()) + 0.5D;

        spawnEdge(player, minX, minY, minZ, maxX, minY, minZ, samplesPerEdge, options);
        spawnEdge(player, minX, minY, maxZ, maxX, minY, maxZ, samplesPerEdge, options);
        spawnEdge(player, minX, maxY, minZ, maxX, maxY, minZ, samplesPerEdge, options);
        spawnEdge(player, minX, maxY, maxZ, maxX, maxY, maxZ, samplesPerEdge, options);
        spawnEdge(player, minX, minY, minZ, minX, maxY, minZ, samplesPerEdge, options);
        spawnEdge(player, maxX, minY, minZ, maxX, maxY, minZ, samplesPerEdge, options);
        spawnEdge(player, minX, minY, maxZ, minX, maxY, maxZ, samplesPerEdge, options);
        spawnEdge(player, maxX, minY, maxZ, maxX, maxY, maxZ, samplesPerEdge, options);
        spawnEdge(player, minX, minY, minZ, minX, minY, maxZ, samplesPerEdge, options);
        spawnEdge(player, maxX, minY, minZ, maxX, minY, maxZ, samplesPerEdge, options);
        spawnEdge(player, minX, maxY, minZ, minX, maxY, maxZ, samplesPerEdge, options);
        spawnEdge(player, maxX, maxY, minZ, maxX, maxY, maxZ, samplesPerEdge, options);
    }

    private void spawnEdge(Player player, double x1, double y1, double z1, double x2, double y2, double z2, int samples, Particle.DustOptions options) {
        for (int index = 0; index <= samples; index++) {
            double progress = index / (double) samples;
            player.spawnParticle(resolveDustParticle(), x1 + ((x2 - x1) * progress), y1 + ((y2 - y1) * progress), z1 + ((z2 - z1) * progress),
                    1, 0.0D, 0.0D, 0.0D, 0.0D, options);
        }
    }

    private String blankPanelText() {
        String blankLine = " ".repeat(FACE_TEXT_WIDTH);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < FACE_TEXT_LINES; index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(blankLine);
        }
        return builder.toString();
    }

    private ItemStack createWand() {
        ItemStack item = new ItemStack(WAND_MATERIAL);
        item.editMeta(meta -> {
            meta.displayName(Components.colorize(plugin.getMessages().text("zone.wand-name", Map.of())));
            meta.lore(plugin.getMessages().lines("zone.wand-lore", Map.of()).stream().map(Components::colorize).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    private Particle resolveDustParticle() {
        try {
            return Particle.valueOf("DUST");
        } catch (IllegalArgumentException ignored) {
            return Particle.valueOf("REDSTONE");
        }
    }

    private void writeZone(ReviveZone zone) {
        String path = "zones." + zone.name();
        config.set(path + ".world", zone.worldName());
        config.set(path + ".min.x", zone.minX());
        config.set(path + ".min.y", zone.minY());
        config.set(path + ".min.z", zone.minZ());
        config.set(path + ".max.x", zone.maxX());
        config.set(path + ".max.y", zone.maxY());
        config.set(path + ".max.z", zone.maxZ());
        config.set(path + ".speed-multiplier", zone.speedMultiplier());
        config.set(path + ".auto-revive", zone.autoRevive());
    }

    private static final class SelectionMarkers {
        private MarkerGroup pos1 = new MarkerGroup();
        private MarkerGroup pos2 = new MarkerGroup();
    }

    private static final class MarkerGroup {
        private final TextDisplay[] panels = new TextDisplay[Face.values().length];
        private final TextDisplay[] labels = new TextDisplay[Face.values().length];
        private TextDisplay coords;
    }

    private record FaceSettings(double offsetX, double offsetY, double offsetZ, float displayWidth, float displayHeight, int lineWidth, float scaleX, float scaleY, float scaleZ) {
    }

    private enum Face {
        NORTH("north", 180.0F, 0.0F),
        SOUTH("south", 0.0F, 0.0F),
        WEST("west", 90.0F, 0.0F),
        EAST("east", -90.0F, 0.0F),
        TOP("top", 0.0F, -90.0F),
        BOTTOM("bottom", 0.0F, 90.0F);

        private final String key;
        private final float yaw;
        private final float pitch;

        Face(String key, float yaw, float pitch) {
            this.key = key;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private FaceSettings settings() {
            return switch (this) {
                case NORTH -> new FaceSettings(0.01D, -0.5D, 0.0D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
                case SOUTH -> new FaceSettings(-0.01D, -0.5D, 0.0D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
                case EAST -> new FaceSettings(0.0D, -0.5D, 0.01D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
                case WEST -> new FaceSettings(0.0D, -0.5D, -0.01D, 1.0F, 1.0F, 220, 1.0F, 1.0F, 1.0F);
                case TOP -> new FaceSettings(-0.01D, 0.0D, 0.5D, 1.0F, 1.0F, 220, 1.01F, 1.01F, 1.01F);
                case BOTTOM -> new FaceSettings(-0.01D, 0.0D, -0.5D, 1.0F, 1.0F, 220, 1.01F, 1.01F, 1.01F);
            };
        }

        private Location panelLocation(Location block) {
            Location base = switch (this) {
                case NORTH -> block.clone().add(0.5D, 0.5D, FACE_INSET);
                case SOUTH -> block.clone().add(0.5D, 0.5D, 1.0D - FACE_INSET);
                case WEST -> block.clone().add(FACE_INSET, 0.5D, 0.5D);
                case EAST -> block.clone().add(1.0D - FACE_INSET, 0.5D, 0.5D);
                case TOP -> block.clone().add(0.5D, 1.0D - FACE_INSET, 0.5D);
                case BOTTOM -> block.clone().add(0.5D, FACE_INSET, 0.5D);
            };
            FaceSettings s = settings();
            return base.add(s.offsetX, s.offsetY, s.offsetZ);
        }

        private Location labelLocation(Location panelLocation) {
            return switch (this) {
                case TOP -> panelLocation.clone().add(0.0D, 0.4D, 0.0D);
                case BOTTOM -> panelLocation.clone().add(0.0D, 0.4D, 0.0D);
                default -> panelLocation.clone().add(0.0D, 0.4D, 0.0D);
            };
        }
    }
}
