package com.aquiles.nexusrevive.config;

import com.aquiles.nexusrevive.util.Components;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Messages {
    private final FileConfiguration config;

    public Messages(FileConfiguration config) {
        this.config = config;
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        List<String> lines = config.getStringList(path);
        if (!lines.isEmpty()) {
            for (String line : lines) {
                sendFormatted(sender, applyPrefix(apply(line, placeholders)));
            }
            return;
        }

        String raw = config.getString(path);
        if (raw == null || raw.isBlank()) {
            return;
        }

        sendFormatted(sender, applyPrefix(apply(raw, placeholders)));
    }

    public Component component(String path, Map<String, String> placeholders) {
        return Components.colorize(text(path, placeholders));
    }

    public List<Component> components(String path, Map<String, String> placeholders) {
        return lines(path, placeholders).stream()
                .map(Components::colorize)
                .toList();
    }

    public String text(String path, Map<String, String> placeholders) {
        return apply(config.getString(path, path), placeholders);
    }

    public String string(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    public int integer(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    public List<String> lines(String path, Map<String, String> placeholders) {
        List<String> configured = config.getStringList(path);
        if (!configured.isEmpty()) {
            List<String> formatted = new ArrayList<>(configured.size());
            for (String line : configured) {
                formatted.add(apply(line, placeholders));
            }
            return formatted;
        }

        String single = config.getString(path);
        if (single == null || single.isBlank()) {
            return List.of();
        }
        return List.of(apply(single, placeholders));
    }

    private void sendFormatted(CommandSender sender, String formatted) {
        if (formatted == null || formatted.isBlank()) {
            return;
        }

        Component message = Components.colorize(formatted);
        if (sender instanceof Player player) {
            player.sendMessage(message);
            return;
        }
        sender.sendMessage(Components.plain(message));
    }

    private String applyPrefix(String input) {
        String prefix = config.getString("general.prefix", "");
        if (prefix == null || prefix.isBlank()) {
            return input;
        }
        return prefix + input;
    }

    private String apply(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }
}

