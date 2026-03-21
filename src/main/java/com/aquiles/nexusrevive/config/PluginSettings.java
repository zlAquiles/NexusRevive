package com.aquiles.nexusrevive.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record PluginSettings(
        Mechanics mechanics,
        Revive revive,
        Carry carry,
        Loot loot,
        Persistence persistence,
        Updater updater,
        Sounds sounds,
        DownedEffects downedEffects,
        Restrictions restrictions,
        DownedInteractions downedInteractions,
        Commands commands,
        Hooks hooks,
        Scoreboard scoreboard
) {
    public static PluginSettings from(FileConfiguration config) {
        return new PluginSettings(
                new Mechanics(
                        config.getDouble("mechanics.downed-health", 1.0D),
                        config.getDouble("mechanics.downed-walk-speed", 0.06D),
                        config.getInt("mechanics.death-delay-seconds", 75),
                        config.getInt("mechanics.invulnerability-seconds", 15),
                        config.getDouble("mechanics.revived-health", 6.0D),
                        config.getInt("mechanics.downed-entry-delay-ticks", 4),
                        config.getDouble("mechanics.downed-entry-knockback-horizontal", 0.16D),
                        config.getDouble("mechanics.downed-entry-knockback-vertical", 0.08D),
                        DeathAction.valueOf(config.getString("mechanics.death-action", "KILL").toUpperCase(Locale.ROOT)),
                        config.getBoolean("mechanics.kill-on-disconnect", true),
                        config.getBoolean("mechanics.disable-death-message-while-downed", true)
                ),
                new Revive(
                        config.getInt("revive.duration-seconds", 10),
                        config.getBoolean("revive.require-sneak", true),
                        config.getBoolean("revive.allow-self-revive", false),
                        config.getDouble("revive.start-distance", 2.0D),
                        config.getDouble("revive.cancel-distance", 4.5D),
                        ZoneMode.valueOf(config.getString("revive.zone-mode", "ANYWHERE").toUpperCase(Locale.ROOT)),
                        config.getBoolean("revive.zones-affect-speed", true),
                        new Revive.AutoReviveFinishEffect(
                                config.getBoolean("revive.auto-revive-finish-effect.enabled", true),
                                parseParticle(config, "revive.auto-revive-finish-effect.primary-particle", "TOTEM_OF_UNDYING", 32, 0.45D, 0.7D, 0.45D, 0.0D),
                                parseParticle(config, "revive.auto-revive-finish-effect.secondary-particle", "HAPPY_VILLAGER", 18, 0.4D, 0.6D, 0.4D, 0.0D),
                                parseSound(config, "revive.auto-revive-finish-effect.sound", "ITEM_TOTEM_USE", 0.9F, 1.15F)
                        ),
                        new ReviveItemRequirement(
                                config.getBoolean("revive.required-item.enabled", false),
                                config.getBoolean("revive.required-item.consume-on-success", true),
                                parseItemRules(config.getConfigurationSection("revive.required-item.items"))
                        )
                ),
                new Carry(
                        config.getBoolean("carry.enabled", true),
                        config.getBoolean("carry.drop-on-picker-damage", false),
                        config.getBoolean("carry.stop-death-timer-while-carried", false),
                        config.getBoolean("carry.allow-downed-dismount", false)
                ),
                new Loot(
                        config.getBoolean("loot.enabled", true),
                        config.getBoolean("loot.debug", false),
                        config.getBoolean("loot.require-sneak", true),
                        config.getBoolean("loot.single-robber-lock", true),
                        config.getBoolean("loot.allow-while-reviving", false),
                        config.getBoolean("loot.allow-while-carried", false),
                        config.getBoolean("loot.allow-main-inventory", true),
                        config.getBoolean("loot.allow-armor", true),
                        config.getBoolean("loot.allow-offhand", true)
                ),
                new Persistence(
                        config.getBoolean("persistence.enabled", false)
                ),
                new Updater(
                        config.getBoolean("updater.enabled", true)
                ),
                new Sounds(
                        parseSound(config, "sounds.reliving"),
                        parseSound(config, "sounds.start-reliving"),
                        parseSound(config, "sounds.stop-reliving"),
                        parseSound(config, "sounds.success-relive")
                ),
                new DownedEffects(
                        config.getBoolean("downed-effects.enabled", true),
                        parseDownedEffects(config.getConfigurationSection("downed-effects.effects"))
                ),
                new Restrictions(
                        ListMode.valueOf(config.getString("restrictions.worlds.mode", "BLACKLIST").toUpperCase(Locale.ROOT)),
                        config.getStringList("restrictions.worlds.list"),
                        parseDamageCauses(config.getStringList("restrictions.ignored-damage-causes"))
                ),
                new DownedInteractions(
                        config.getBoolean("downed-interactions.allow-move", true),
                        config.getBoolean("downed-interactions.allow-interact", false),
                        config.getBoolean("downed-interactions.allow-entity-interact", false),
                        config.getBoolean("downed-interactions.allow-block-break", false),
                        config.getBoolean("downed-interactions.allow-block-place", false),
                        config.getBoolean("downed-interactions.allow-teleport", false),
                        config.getBoolean("downed-interactions.allow-projectiles", false),
                        config.getBoolean("downed-interactions.allow-consume", false),
                        config.getBoolean("downed-interactions.allow-inventory", false),
                        config.getBoolean("downed-interactions.allow-offhand", false),
                        config.getBoolean("downed-interactions.allow-helmet", false),
                        config.getBoolean("downed-interactions.allow-chestplate", false),
                        config.getBoolean("downed-interactions.allow-leggings", false),
                        config.getBoolean("downed-interactions.allow-boots", false),
                        config.getBoolean("downed-interactions.allow-item-drop", false),
                        config.getBoolean("downed-interactions.allow-item-pickup", false),
                        config.getBoolean("downed-interactions.allow-gliding", false),
                        config.getBoolean("downed-interactions.allow-fall-damage", false),
                        config.getDouble("downed-interactions.max-fall-distance", -1.0D)
                ),
                new Commands(
                        ListMode.valueOf(config.getString("commands.mode", "BLACKLIST").toUpperCase(Locale.ROOT)),
                        config.getStringList("commands.list").stream()
                                .map(entry -> entry.toLowerCase(Locale.ROOT))
                                .collect(Collectors.toList())
                ),
                new Hooks(
                        new VaultHook(
                                config.getBoolean("hooks.vault.enabled", true),
                                config.getDouble("hooks.vault.revive-cost", 0.0D)
                        ),
                        new WorldGuardHook(
                                config.getBoolean("hooks.worldguard.enabled", true)
                        ),
                        new DeluxeCombatHook(
                                config.getBoolean("hooks.deluxecombat.enabled", true),
                                config.getBoolean("hooks.deluxecombat.allow-revive-in-combat", true),
                                config.getBoolean("hooks.deluxecombat.allow-carry-in-combat", false),
                                config.getBoolean("hooks.deluxecombat.respect-pvp-protection", true)
                        ),
                        new CmiHook(
                                config.getBoolean("hooks.cmi.enabled", true),
                                config.getBoolean("hooks.cmi.respect-god-mode", true),
                                config.getBoolean("hooks.cmi.respect-vanish", true)
                        ),
                        new EssentialsHook(
                                config.getBoolean("hooks.essentials.enabled", true),
                                config.getBoolean("hooks.essentials.respect-god-mode", true),
                                config.getBoolean("hooks.essentials.respect-vanish", true)
                        ),
                        new SuperVanishHook(
                                config.getBoolean("hooks.supervanish.enabled", true),
                                config.getBoolean("hooks.supervanish.respect-vanish", true)
                        ),
                        new QualityArmoryHook(
                                config.getBoolean("hooks.qualityarmory.enabled", true)
                        ),
                        new WeaponMechanicsHook(
                                config.getBoolean("hooks.weaponmechanics.enabled", true)
                        )
                ),
                new Scoreboard(
                        config.getBoolean("scoreboard.enabled", true),
                        config.getLong("scoreboard.update-interval-ticks", 10L),
                        config.getBoolean("scoreboard.show-for-downed", true),
                        config.getBoolean("scoreboard.show-for-reviver", true),
                        config.getBoolean("scoreboard.show-for-picker", true)
                )
        );
    }

    private static Sounds.ConfiguredSound parseSound(FileConfiguration config, String path) {
        return parseSound(config, path, "BLOCK_NOTE_BLOCK_HARP", 1.0F, 1.0F);
    }

    private static Sounds.ConfiguredSound parseSound(FileConfiguration config, String path, String defaultSound, float defaultVolume, float defaultPitch) {
        return new Sounds.ConfiguredSound(
                config.getBoolean(path + ".enabled", false),
                config.getString(path + ".sound", defaultSound),
                (float) config.getDouble(path + ".volume", defaultVolume),
                (float) config.getDouble(path + ".pitch", config.getDouble(path + ".yaw", defaultPitch)),
                Math.max(0L, config.getLong(path + ".cooldown-ticks", 0L))
        );
    }

    private static Revive.ConfiguredParticle parseParticle(
            FileConfiguration config,
            String path,
            String defaultParticle,
            int defaultCount,
            double defaultOffsetX,
            double defaultOffsetY,
            double defaultOffsetZ,
            double defaultExtra
    ) {
        return new Revive.ConfiguredParticle(
                config.getBoolean(path + ".enabled", true),
                config.getString(path + ".particle", defaultParticle),
                Math.max(0, config.getInt(path + ".count", defaultCount)),
                config.getDouble(path + ".offset-x", defaultOffsetX),
                config.getDouble(path + ".offset-y", defaultOffsetY),
                config.getDouble(path + ".offset-z", defaultOffsetZ),
                config.getDouble(path + ".extra", defaultExtra)
        );
    }

    private static Map<String, ReviveItemRequirement.ItemRule> parseItemRules(ConfigurationSection section) {
        Map<String, ReviveItemRequirement.ItemRule> rules = new LinkedHashMap<>();
        if (section == null) {
            return rules;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }

            String rawMaterial = itemSection.getString("material");
            Material material = null;
            if (rawMaterial != null && !rawMaterial.isBlank()) {
                try {
                    material = Material.valueOf(rawMaterial.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }

            rules.put(
                    key,
                    new ReviveItemRequirement.ItemRule(
                            material,
                            itemSection.getBoolean("has_custom_model_data", false),
                            itemSection.getInt("custom_model_data", 0),
                            itemSection.getString("name_contains", "")
                    )
            );
        }
        return rules;
    }

    private static Map<String, DownedEffects.EffectRule> parseDownedEffects(ConfigurationSection section) {
        Map<String, DownedEffects.EffectRule> rules = new LinkedHashMap<>();
        if (section == null) {
            return rules;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection effectSection = section.getConfigurationSection(key);
            if (effectSection == null) {
                continue;
            }

            PotionEffectType type = parsePotionEffectType(effectSection.getString("type", key));
            if (type == null) {
                continue;
            }

            rules.put(
                    key,
                    new DownedEffects.EffectRule(
                            type,
                            Math.max(0, effectSection.getInt("amplifier", 0)),
                            effectSection.getBoolean("ambient", false),
                            effectSection.getBoolean("particles", false),
                            effectSection.getBoolean("icon", false)
                    )
            );
        }
        return rules;
    }

    private static PotionEffectType parsePotionEffectType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }

        String normalized = rawType.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        PotionEffectType direct = Registry.EFFECT.get(NamespacedKey.minecraft(normalized));
        if (direct != null) {
            return direct;
        }

        NamespacedKey customKey = NamespacedKey.fromString(normalized);
        if (customKey == null) {
            return null;
        }
        return Registry.EFFECT.get(customKey);
    }

    private static Set<EntityDamageEvent.DamageCause> parseDamageCauses(List<String> rawCauses) {
        EnumSet<EntityDamageEvent.DamageCause> causes = EnumSet.noneOf(EntityDamageEvent.DamageCause.class);
        for (String rawCause : rawCauses) {
            try {
                causes.add(EntityDamageEvent.DamageCause.valueOf(rawCause.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return causes;
    }

    public enum DeathAction {
        KILL,
        REVIVE
    }

    public enum ListMode {
        BLACKLIST,
        WHITELIST;

        public boolean allows(String value, List<String> configured) {
            List<String> normalized = configured.stream()
                    .map(entry -> entry.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
            boolean contains = normalized.contains(value.toLowerCase(Locale.ROOT));
            return this == BLACKLIST ? !contains : contains;
        }
    }

    public enum ZoneMode {
        ANYWHERE,
        ONLY_IN_ZONES
    }

    public record Mechanics(
            double downedHealth,
            double downedWalkSpeed,
            int deathDelaySeconds,
            int invulnerabilitySeconds,
            double revivedHealth,
            int downedEntryDelayTicks,
            double downedEntryKnockbackHorizontal,
            double downedEntryKnockbackVertical,
            DeathAction deathAction,
            boolean killOnDisconnect,
            boolean disableDeathMessageWhileDowned
    ) {
    }

    public record Revive(
            int durationSeconds,
            boolean requireSneak,
            boolean allowSelfRevive,
            double startDistance,
            double cancelDistance,
            ZoneMode zoneMode,
            boolean zonesAffectSpeed,
            AutoReviveFinishEffect autoReviveFinishEffect,
            ReviveItemRequirement requiredItem
    ) {
        public record AutoReviveFinishEffect(
                boolean enabled,
                ConfiguredParticle primaryParticle,
                ConfiguredParticle secondaryParticle,
                Sounds.ConfiguredSound sound
        ) {
        }

        public record ConfiguredParticle(
                boolean enabled,
                String particle,
                int count,
                double offsetX,
                double offsetY,
                double offsetZ,
                double extra
        ) {
        }
    }

    public record ReviveItemRequirement(
            boolean enabled,
            boolean consumeOnSuccess,
            Map<String, ItemRule> items
    ) {
        public record ItemRule(
                Material material,
                boolean hasCustomModelData,
                int customModelData,
                String nameContains
        ) {
        }
    }

    public record Carry(
            boolean enabled,
            boolean dropOnPickerDamage,
            boolean stopDeathTimerWhileCarried,
            boolean allowDownedDismount
    ) {
    }

    public record Loot(
            boolean enabled,
            boolean debug,
            boolean requireSneak,
            boolean singleRobberLock,
            boolean allowWhileReviving,
            boolean allowWhileCarried,
            boolean allowMainInventory,
            boolean allowArmor,
            boolean allowOffhand
    ) {
    }

    public record Persistence(
            boolean enabled
    ) {
    }

    public record Updater(
            boolean enabled
    ) {
    }

    public record Sounds(
            ConfiguredSound reliving,
            ConfiguredSound startReliving,
            ConfiguredSound stopReliving,
            ConfiguredSound successRelive
    ) {
        public record ConfiguredSound(
                boolean enabled,
                String sound,
                float volume,
                float pitch,
                long cooldownTicks
        ) {
        }
    }

    public record DownedEffects(
            boolean enabled,
            Map<String, EffectRule> effects
    ) {
        public record EffectRule(
                PotionEffectType type,
                int amplifier,
                boolean ambient,
                boolean particles,
                boolean icon
        ) {
        }
    }

    public record Restrictions(
            ListMode worldMode,
            List<String> worlds,
            Set<EntityDamageEvent.DamageCause> ignoredDamageCauses
    ) {
    }

    public record DownedInteractions(
            boolean allowMove,
            boolean allowInteract,
            boolean allowEntityInteract,
            boolean allowBlockBreak,
            boolean allowBlockPlace,
            boolean allowTeleport,
            boolean allowProjectiles,
            boolean allowConsume,
            boolean allowInventory,
            boolean allowOffhand,
            boolean allowHelmet,
            boolean allowChestplate,
            boolean allowLeggings,
            boolean allowBoots,
            boolean allowItemDrop,
            boolean allowItemPickup,
            boolean allowGliding,
            boolean allowFallDamage,
            double maxFallDistance
    ) {
    }

    public record Commands(
            ListMode mode,
            List<String> list
    ) {
    }

    public record Hooks(
            VaultHook vault,
            WorldGuardHook worldGuard,
            DeluxeCombatHook deluxeCombat,
            CmiHook cmi,
            EssentialsHook essentials,
            SuperVanishHook superVanish,
            QualityArmoryHook qualityArmory,
            WeaponMechanicsHook weaponMechanics
    ) {
    }

    public record VaultHook(
            boolean enabled,
            double reviveCost
    ) {
    }

    public record WorldGuardHook(
            boolean enabled
    ) {
    }

    public record DeluxeCombatHook(
            boolean enabled,
            boolean allowReviveInCombat,
            boolean allowCarryInCombat,
            boolean respectPvpProtection
    ) {
    }

    public record CmiHook(
            boolean enabled,
            boolean respectGodMode,
            boolean respectVanish
    ) {
    }

    public record EssentialsHook(
            boolean enabled,
            boolean respectGodMode,
            boolean respectVanish
    ) {
    }

    public record SuperVanishHook(
            boolean enabled,
            boolean respectVanish
    ) {
    }

    public record QualityArmoryHook(
            boolean enabled
    ) {
    }

    public record WeaponMechanicsHook(
            boolean enabled
    ) {
    }

    public record Scoreboard(
            boolean enabled,
            long updateIntervalTicks,
            boolean showForDowned,
            boolean showForReviver,
            boolean showForPicker
    ) {
    }
}

