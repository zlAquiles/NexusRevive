package com.aquiles.nexusrevive.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record GpsSettings(
        DisplayMode displayMode,
        long updateIntervalTicks,
        BossBar bossBar,
        ActionBar actionBar,
        Hologram hologram,
        Gui gui
) {
    private static final List<String> DEFAULT_LAYOUT = List.of(
            "###+I+###",
            "#TTTTTTT#",
            "#TTTEETT#",
            "#TTTTTTT#",
            "#..P.C.N#",
            "###+.++##"
    );

    public static GpsSettings from(FileConfiguration config) {
        List<String> layout = normalizeLayout(config.getStringList("gui.layout"));
        LayoutSlots slots = parseLayout(layout);
        return new GpsSettings(
                DisplayMode.from(config.getString("display-mode", "BOSSBAR")),
                Math.max(1L, config.getLong("update-interval-ticks", 4L)),
                new BossBar(
                        config.getString("bossbar.format", "&b<victim> &8| &f<distance> &8| &a<arrow>"),
                        config.getString("bossbar.arrows.north", "↑"),
                        config.getString("bossbar.arrows.north-east", "↗"),
                        config.getString("bossbar.arrows.east", "→"),
                        config.getString("bossbar.arrows.south-east", "↘"),
                        config.getString("bossbar.arrows.south", "↓"),
                        config.getString("bossbar.arrows.south-west", "↙"),
                        config.getString("bossbar.arrows.west", "←"),
                        config.getString("bossbar.arrows.north-west", "↖"),
                        config.getDouble("bossbar.progress", 1.0D),
                        config.getDouble("bossbar.arrive-distance", 5.0D),
                        config.getString("bossbar.other-world-text", "otro mundo")
                ),
                new ActionBar(
                        config.getString("actionbar.format", "&bGPS &8| &f<distance> &8| &a<arrow>")
                ),
                new Hologram(
                        config.getString("hologram.format", "&a<pointer> &f<distance>"),
                        config.getString("hologram.pointer-text", "▲"),
                        config.getDouble("hologram.distance", config.getDouble("hologram.offset-distance", 2.35D)),
                        config.getDouble("hologram.offset-height", 0.15D),
                        (float) config.getDouble("hologram.scale", 1.0D),
                        Math.max(0, config.getInt("hologram.interpolation-duration", 4)),
                        config.getBoolean("hologram.shadowed", true),
                        config.getBoolean("hologram.see-through", false),
                        config.getBoolean("hologram.default-background", false)
                ),
                new Gui(
                        config.getString("gui.title", "&8Nexus GPS"),
                        layout,
                        slots.targetSlots(),
                        slots.emptySlots(),
                        slots.infoSlot(),
                        slots.previousSlot(),
                        slots.nextSlot(),
                        slots.closeSlot(),
                        parseItem(config, "gui.items.border", Material.GRAY_STAINED_GLASS_PANE, " "),
                        parseItem(config, "gui.items.filler", Material.BLACK_STAINED_GLASS_PANE, " "),
                        parseItem(config, "gui.items.accent", Material.CYAN_STAINED_GLASS_PANE, " "),
                        parseItem(config, "gui.items.info", Material.COMPASS, "&bNexus GPS"),
                        parseItem(config, "gui.items.empty", Material.RECOVERY_COMPASS, "&cSin objetivos"),
                        parseItem(config, "gui.items.previous", Material.SPECTRAL_ARROW, "&7Pagina anterior"),
                        parseItem(config, "gui.items.previous-disabled", Material.GRAY_DYE, "&8Pagina anterior"),
                        parseItem(config, "gui.items.next", Material.SPECTRAL_ARROW, "&7Pagina siguiente"),
                        parseItem(config, "gui.items.next-disabled", Material.GRAY_DYE, "&8Pagina siguiente"),
                        parseItem(config, "gui.items.close", Material.BARRIER, "&cCerrar"),
                        new TargetItem(
                                config.getString("gui.target.name", "&c<victim>"),
                                config.getStringList("gui.target.lore")
                        )
                )
        );
    }

    private static List<String> normalizeLayout(List<String> raw) {
        List<String> source = raw == null || raw.isEmpty() ? DEFAULT_LAYOUT : raw;
        List<String> layout = new ArrayList<>();
        for (String line : source) {
            String normalized = line == null ? "" : line;
            if (normalized.length() < 9) {
                normalized = normalized + ".".repeat(9 - normalized.length());
            }
            layout.add(normalized.substring(0, 9));
        }
        if (layout.isEmpty()) {
            return new ArrayList<>(DEFAULT_LAYOUT);
        }
        return layout.subList(0, Math.min(6, layout.size()));
    }

    private static LayoutSlots parseLayout(List<String> layout) {
        List<Integer> targetSlots = new ArrayList<>();
        List<Integer> emptySlots = new ArrayList<>();
        int infoSlot = -1;
        int previousSlot = -1;
        int nextSlot = -1;
        int closeSlot = -1;

        for (int row = 0; row < layout.size(); row++) {
            String line = layout.get(row);
            for (int column = 0; column < 9; column++) {
                int slot = row * 9 + column;
                switch (line.charAt(column)) {
                    case 'T' -> targetSlots.add(slot);
                    case 'E' -> emptySlots.add(slot);
                    case 'I' -> infoSlot = slot;
                    case 'P' -> previousSlot = slot;
                    case 'N' -> nextSlot = slot;
                    case 'C' -> closeSlot = slot;
                    default -> {
                    }
                }
            }
        }

        if (targetSlots.isEmpty()) {
            for (int slot = 10; slot <= 34; slot++) {
                if (slot % 9 != 0 && slot % 9 != 8) {
                    targetSlots.add(slot);
                }
            }
        }
        return new LayoutSlots(targetSlots, emptySlots, infoSlot, previousSlot, nextSlot, closeSlot);
    }

    private static GuiItem parseItem(FileConfiguration config, String path, Material fallbackMaterial, String fallbackName) {
        return new GuiItem(
                material(config.getString(path + ".material", fallbackMaterial.name()), fallbackMaterial),
                config.getString(path + ".name", fallbackName),
                config.getStringList(path + ".lore")
        );
    }

    private static Material material(String value, Material fallback) {
        Material material = Material.matchMaterial(value);
        return material == null ? fallback : material;
    }

    public enum DisplayMode {
        NONE,
        BOSSBAR,
        HOLOGRAM,
        BOSSBAR_AND_HOLOGRAM,
        ACTIONBAR;

        public static DisplayMode from(String raw) {
            if (raw == null || raw.isBlank()) {
                return BOSSBAR;
            }

            String normalized = raw.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_')
                    .replace('+', '_');

            if ("BOSSBAR_HOLOGRAM".equals(normalized)) {
                return BOSSBAR_AND_HOLOGRAM;
            }

            try {
                return DisplayMode.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return BOSSBAR;
            }
        }

        public boolean usesBossBar() {
            return this == BOSSBAR || this == BOSSBAR_AND_HOLOGRAM;
        }

        public boolean usesHologram() {
            return this == HOLOGRAM || this == BOSSBAR_AND_HOLOGRAM;
        }

        public boolean usesActionBar() {
            return this == ACTIONBAR;
        }
    }

    public record BossBar(
            String format,
            String north,
            String northEast,
            String east,
            String southEast,
            String south,
            String southWest,
            String west,
            String northWest,
            double progress,
            double arriveDistance,
            String otherWorldText
    ) {
    }

    public record ActionBar(
            String format
    ) {
    }

    public record Hologram(
            String format,
            String pointerText,
            double distance,
            double offsetHeight,
            float scale,
            int interpolationDuration,
            boolean shadowed,
            boolean seeThrough,
            boolean defaultBackground
    ) {
    }

    public record Gui(
            String title,
            List<String> layout,
            List<Integer> targetSlots,
            List<Integer> emptySlots,
            int infoSlot,
            int previousSlot,
            int nextSlot,
            int closeSlot,
            GuiItem border,
            GuiItem filler,
            GuiItem accent,
            GuiItem info,
            GuiItem empty,
            GuiItem previous,
            GuiItem previousDisabled,
            GuiItem next,
            GuiItem nextDisabled,
            GuiItem close,
            TargetItem target
    ) {
        public int size() {
            return Math.max(9, layout.size() * 9);
        }
    }

    public record GuiItem(
            Material material,
            String name,
            List<String> lore
    ) {
    }

    public record TargetItem(
            String name,
            List<String> lore
    ) {
    }

    private record LayoutSlots(
            List<Integer> targetSlots,
            List<Integer> emptySlots,
            int infoSlot,
            int previousSlot,
            int nextSlot,
            int closeSlot
    ) {
    }
}
