package com.aquiles.nexusrevive.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import com.aquiles.nexusrevive.NexusRevivePlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class NexusRevivePlaceholderExpansion extends PlaceholderExpansion {
    private final NexusRevivePlugin plugin;

    public NexusRevivePlaceholderExpansion(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "nexusrevive";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }

        Map<String, String> placeholders = resolvePlaceholders(player);
        return switch (params.toLowerCase()) {
            case "status" -> placeholders.getOrDefault("status", "NONE");
            case "death_delay" -> placeholders.getOrDefault("death_delay", "0");
            case "invulnerability" -> placeholders.getOrDefault("invulnerability", "0");
            case "reviver" -> placeholders.getOrDefault("reviver", "Nadie");
            case "picker" -> placeholders.getOrDefault("picker", "Nadie");
            case "attacker" -> placeholders.getOrDefault("attacker", "Desconocido");
            case "victim" -> placeholders.getOrDefault("victim", player.getName());
            case "progress" -> placeholders.getOrDefault("progress", "0");
            case "auto_revive_zone" -> placeholders.getOrDefault("auto_revive_zone", "none");
            case "in_auto_revive_zone" -> placeholders.getOrDefault("in_auto_revive_zone", "false");
            case "auto_reviving" -> placeholders.getOrDefault("auto_reviving", "false");
            case "death_delay_bar" -> placeholders.getOrDefault("death_delay_bar", "");
            case "invulnerability_bar" -> placeholders.getOrDefault("invulnerability_bar", "");
            case "progress_bar" -> placeholders.getOrDefault("progress_bar", "");
            default -> null;
        };
    }

    private Map<String, String> resolvePlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>(plugin.getDownedService().createVictimHudPlaceholders(player));
        if (!placeholders.isEmpty()) {
            return placeholders;
        }

        placeholders.putAll(plugin.getDownedService().createReviverHudPlaceholders(player));
        if (!placeholders.isEmpty()) {
            return placeholders;
        }

        placeholders.putAll(plugin.getDownedService().createPickerHudPlaceholders(player));
        return placeholders;
    }
}

