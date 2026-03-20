package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EventActionsService {
    private static final List<String> KNOWN_EVENTS = List.of(
            "events.player-downed",
            "events.player-start-revive",
            "events.player-stop-revive",
            "events.player-revived",
            "events.player-picked-up",
            "events.player-dropped",
            "events.player-final-death"
    );

    private final NexusRevivePlugin plugin;
    private final Map<String, ConfiguredEvent> events = new HashMap<>();

    public EventActionsService(NexusRevivePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        events.clear();
        FileConfiguration config = plugin.getConfig();
        for (String path : KNOWN_EVENTS) {
            events.put(path, new ConfiguredEvent(
                    config.getBoolean(path + ".enabled", false),
                    config.getStringList(path + ".commands")
            ));
        }
    }

    public void run(String path, Map<String, String> placeholders, Map<String, ? extends CommandSender> senders) {
        ConfiguredEvent configuredEvent = events.get(path);
        if (configuredEvent == null || !configuredEvent.enabled() || configuredEvent.commands().isEmpty()) {
            return;
        }

        for (String rawCommand : configuredEvent.commands()) {
            dispatch(rawCommand, placeholders, senders);
        }
    }

    private void dispatch(String rawCommand, Map<String, String> placeholders, Map<String, ? extends CommandSender> senders) {
        String command = apply(rawCommand, placeholders).trim();
        if (command.isEmpty()) {
            return;
        }

        CommandSender sender = plugin.getServer().getConsoleSender();
        if (command.startsWith("[") && command.contains("]")) {
            int closeIndex = command.indexOf(']');
            String senderKey = command.substring(1, closeIndex).trim().toLowerCase(Locale.ROOT);
            CommandSender selected = switch (senderKey) {
                case "console" -> plugin.getServer().getConsoleSender();
                case "victim", "attacker", "reviver", "picker" -> senders.get(senderKey);
                default -> plugin.getServer().getConsoleSender();
            };
            if (selected == null) {
                return;
            }
            sender = selected;
            command = command.substring(closeIndex + 1).trim();
        }

        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (!command.isBlank()) {
            Bukkit.dispatchCommand(sender, command);
        }
    }

    private String apply(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private record ConfiguredEvent(boolean enabled, List<String> commands) {
    }
}

