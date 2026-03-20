package com.aquiles.nexusrevive.listener;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class CombatListener implements Listener {
    private final NexusRevivePlugin plugin;

    public CombatListener(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (plugin.getDownedService().isDowned(victim)) {
            if (!plugin.getDownedService().canTakeDamage(victim)) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            plugin.getDownedService().killDowned(victim);
            return;
        }

        if (victim.getHealth() - event.getFinalDamage() > 0.0D) {
            return;
        }

        Player attacker = event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player
                ? player
                : null;

        if (plugin.getDownedService().tryDown(victim, attacker, event.getCause(), event.getFinalDamage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        boolean pendingFinalDeath = plugin.getDownedService().isPendingFinalDeath(player);
        if (plugin.getDownedService().isDowned(player) || pendingFinalDeath) {
            plugin.getDownedService().cleanupAfterDeath(player, true);
        }
        if (pendingFinalDeath && plugin.getPluginSettings().mechanics().disableDeathMessageWhileDowned()) {
            event.deathMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getDownedService().isDowned(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getDownedService().cleanupAfterRespawn(event.getPlayer());
    }
}

