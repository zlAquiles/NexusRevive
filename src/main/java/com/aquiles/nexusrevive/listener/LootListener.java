package com.aquiles.nexusrevive.listener;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.service.LootService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class LootListener implements Listener {
    private final NexusRevivePlugin plugin;

    public LootListener(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!plugin.getLootService().isLootInventory(event.getView().getTopInventory())) {
            return;
        }

        plugin.getLootService().debug("Listener click player=" + player.getName()
                + " rawSlot=" + event.getRawSlot()
                + " slot=" + event.getSlot()
                + " click=" + event.getClick()
                + " action=" + event.getAction()
                + " clickedInv=" + (event.getClickedInventory() == null ? "null" : event.getClickedInventory().getType()));
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            plugin.getLootService().debug("Listener ignored click outside top inventory.");
            return;
        }
        int rawSlot = event.getRawSlot();
        var clickType = event.getClick();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getLootService().handleLootClick(player, rawSlot, clickType)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (plugin.getLootService().isLootInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && plugin.getLootService().isLootInventory(event.getInventory())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.getLootService().isLootInventory(player.getOpenInventory().getTopInventory())) {
                    plugin.getLootService().debug("Close ignored because player still has loot inventory open: " + player.getName());
                    return;
                }
                plugin.getLootService().handleLootClose(player);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getLootService().closeForPlayer(event.getPlayer());
    }
}

