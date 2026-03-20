package com.aquiles.nexusrevive.listener;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.model.Selection;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

public final class ZoneSelectionListener implements Listener {
    private final NexusRevivePlugin plugin;

    public ZoneSelectionListener(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || event.getItem() == null) {
            return;
        }
        if (!plugin.getZoneService().isWand(event.getItem())) {
            return;
        }

        Selection selection = plugin.getZoneService().selection(event.getPlayer());
        if (!selection.isSelectorEnabled()) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.getZoneService().setPosition(event.getPlayer(), true, event.getClickedBlock().getLocation());
            plugin.getMessages().send(event.getPlayer(), "zone.pos1", locationPlaceholders(event.getClickedBlock().getLocation()));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.getZoneService().setPosition(event.getPlayer(), false, event.getClickedBlock().getLocation());
            plugin.getMessages().send(event.getPlayer(), "zone.pos2", locationPlaceholders(event.getClickedBlock().getLocation()));
        } else {
            return;
        }

        if (plugin.getZoneService().hasCompleteSelection(event.getPlayer())) {
            if (plugin.getZoneService().hasWorldMismatch(event.getPlayer())) {
                plugin.getMessages().send(event.getPlayer(), "zone.world-mismatch");
            } else {
                plugin.getMessages().send(event.getPlayer(), "zone.selection-ready");
            }
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getZoneService().hideMarkersFrom(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getZoneService().clear(event.getPlayer());
    }

    private Map<String, String> locationPlaceholders(org.bukkit.Location location) {
        return Map.of(
                "x", Integer.toString(location.getBlockX()),
                "y", Integer.toString(location.getBlockY()),
                "z", Integer.toString(location.getBlockZ())
        );
    }
}
