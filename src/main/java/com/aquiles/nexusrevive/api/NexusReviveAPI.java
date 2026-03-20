package com.aquiles.nexusrevive.api;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.model.DownedPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NexusReviveAPI {
    private NexusReviveAPI() {
    }

    public static boolean isAvailable() {
        return plugin() != null;
    }

    public static boolean isDowned(Player player) {
        NexusRevivePlugin plugin = plugin();
        return plugin != null && plugin.getDownedService().isDowned(player);
    }

    public static boolean isReviving(Player player) {
        NexusRevivePlugin plugin = plugin();
        return plugin != null && plugin.getDownedService().getReviveSession(player).isPresent();
    }

    public static Optional<DownedPlayer> getDownedPlayer(Player player) {
        NexusRevivePlugin plugin = plugin();
        if (plugin == null) {
            return Optional.empty();
        }
        return plugin.getDownedService().getDownedState(player);
    }

    public static Optional<Player> getReviveVictim(Player reviver) {
        NexusRevivePlugin plugin = plugin();
        if (plugin == null) {
            return Optional.empty();
        }
        return plugin.getDownedService().getReviveVictim(reviver);
    }

    public static Map<UUID, DownedPlayer> getDownedPlayers() {
        NexusRevivePlugin plugin = plugin();
        if (plugin == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(plugin.getDownedService().getDownedPlayers());
    }

    public static boolean revivePlayer(Player player) {
        NexusRevivePlugin plugin = plugin();
        return plugin != null && plugin.getDownedService().revive(player, null);
    }

    public static boolean revivePlayer(Player player, Player reviver) {
        NexusRevivePlugin plugin = plugin();
        return plugin != null && plugin.getDownedService().revive(player, reviver);
    }

    public static boolean killDowned(Player player) {
        NexusRevivePlugin plugin = plugin();
        return plugin != null && plugin.getDownedService().killDowned(player);
    }

    public static boolean downPlayer(Player player) {
        return downPlayer(player, null, EntityDamageEvent.DamageCause.CUSTOM);
    }

    public static boolean downPlayer(Player player, EntityDamageEvent.DamageCause cause) {
        return downPlayer(player, null, cause);
    }

    public static boolean downPlayer(Player player, Player attacker) {
        return downPlayer(player, attacker, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
    }

    public static boolean downPlayer(Player player, EntityDamageEvent.DamageCause cause, Player attacker) {
        return downPlayer(player, attacker, cause);
    }

    private static boolean downPlayer(Player player, Player attacker, EntityDamageEvent.DamageCause cause) {
        NexusRevivePlugin plugin = plugin();
        return plugin != null && plugin.getDownedService().tryDown(player, attacker, cause);
    }

    private static NexusRevivePlugin plugin() {
        NexusRevivePlugin plugin = NexusRevivePlugin.getInstance();
        if (plugin != null) {
            return plugin;
        }

        if (Bukkit.getPluginManager().getPlugin("NexusRevive") instanceof NexusRevivePlugin loaded) {
            return loaded;
        }
        return null;
    }
}

