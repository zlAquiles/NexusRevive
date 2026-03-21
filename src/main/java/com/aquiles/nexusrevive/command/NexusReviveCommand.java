package com.aquiles.nexusrevive.command;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.model.ReviveZone;
import com.aquiles.nexusrevive.model.Selection;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class NexusReviveCommand implements CommandExecutor, TabCompleter {
    private final NexusRevivePlugin plugin;

    public NexusReviveCommand(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(sender, "command.help");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "down" -> handleDown(sender, args);
            case "revive" -> handleRevive(sender, args);
            case "kill" -> handleKill(sender, args);
            case "gps" -> handleGps(sender);
            case "zone" -> handleZone(sender, args);
            default -> {
                plugin.getMessages().send(sender, "command.help");
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexusrevive.command.reload")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        plugin.reloadPlugin();
        plugin.getMessages().send(sender, "general.reloaded");
        return true;
    }

    private boolean handleDown(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusrevive.command.down")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args.length > 1 ? args[1] : "");
        if (target == null) {
            plugin.getMessages().send(sender, "general.player-not-found");
            return true;
        }
        boolean result = plugin.getDownedService().tryDown(target, sender instanceof Player player ? player : null, EntityDamageEvent.DamageCause.CUSTOM);
        plugin.getMessages().send(sender, result ? "command.downed" : "command.already-downed", Map.of("victim", target.getName()));
        return true;
    }

    private boolean handleRevive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusrevive.command.revive")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args.length > 1 ? args[1] : "");
        if (target == null) {
            plugin.getMessages().send(sender, "general.player-not-found");
            return true;
        }
        boolean result = plugin.getDownedService().revive(target, sender instanceof Player player ? player : null);
        plugin.getMessages().send(sender, result ? "command.revived" : "command.not-downed", Map.of("victim", target.getName()));
        return true;
    }

    private boolean handleKill(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexusrevive.command.kill")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args.length > 1 ? args[1] : "");
        if (target == null) {
            plugin.getMessages().send(sender, "general.player-not-found");
            return true;
        }
        boolean result = plugin.getDownedService().killDowned(target);
        plugin.getMessages().send(sender, result ? "command.killed" : "command.not-downed", Map.of("victim", target.getName()));
        return true;
    }

    private boolean handleGps(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.only-player");
            return true;
        }
        if (!sender.hasPermission("nexusrevive.command.gps")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        if (plugin.getGpsService().getDownedPlayers(player).isEmpty()) {
            plugin.getMessages().send(player, "gps.no-downed");
            return true;
        }
        player.openInventory(plugin.getGpsService().buildMenu(player, 0));
        return true;
    }

    private boolean handleZone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.only-player");
            return true;
        }
        if (!sender.hasPermission("nexusrevive.command.zone")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "zone.help");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "wand" -> {
                plugin.getZoneService().giveWand(player);
                plugin.getMessages().send(player, "zone.wand-given");
                yield true;
            }
            case "create" -> {
                Selection selection = plugin.getZoneService().selection(player);
                if (args.length < 3) {
                    plugin.getMessages().send(player, "zone.help");
                    yield true;
                }
                if (!selection.isComplete()) {
                    plugin.getMessages().send(player, "zone.need-selection");
                    yield true;
                }
                if (selection.hasWorldMismatch()) {
                    plugin.getMessages().send(player, "zone.world-mismatch");
                    yield true;
                }
                boolean result = plugin.getZoneService().createZone(args[2], player, 1.0D);
                plugin.getMessages().send(player, result ? "zone.created" : "zone.create-failed", Map.of("name", args[2]));
                yield true;
            }
            case "remove" -> {
                if (args.length < 3) {
                    plugin.getMessages().send(player, "zone.help");
                    yield true;
                }
                boolean result = plugin.getZoneService().removeZone(args[2]);
                plugin.getMessages().send(player, result ? "zone.removed" : "zone.not-found", Map.of("name", args[2]));
                yield true;
            }
            case "list" -> {
                String names = plugin.getZoneService().getZones().stream()
                        .map(ReviveZone::name)
                        .collect(Collectors.joining(", "));
                plugin.getMessages().send(player, "zone.list", Map.of("zones", names.isBlank() ? "sin zonas" : names));
                yield true;
            }
            case "speed" -> {
                if (args.length < 4) {
                    plugin.getMessages().send(player, "zone.help");
                    yield true;
                }
                try {
                    double speed = Double.parseDouble(args[3]);
                    boolean result = plugin.getZoneService().updateSpeed(args[2], speed);
                    plugin.getMessages().send(player, result ? "zone.speed-updated" : "zone.not-found",
                            Map.of("name", args[2], "speed", Double.toString(speed)));
                } catch (NumberFormatException exception) {
                    plugin.getMessages().send(player, "zone.invalid-speed");
                }
                yield true;
            }
            default -> {
                plugin.getMessages().send(player, "zone.help");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("reload", "down", "revive", "kill", "gps", "zone"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("zone")) {
            return filter(args[1], List.of("wand", "create", "remove", "list", "speed"));
        }
        if (args.length == 2 && List.of("down", "revive", "kill").contains(args[0].toLowerCase())) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && List.of("remove", "speed").contains(args[1].toLowerCase())) {
            return filter(args[2], plugin.getZoneService().getZones().stream().map(ReviveZone::name).toList());
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                matches.add(option);
            }
        }
        return matches;
    }
}

