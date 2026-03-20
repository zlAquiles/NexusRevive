package com.aquiles.nexusrevive.listener;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.service.GpsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class GpsMenuListener implements Listener {
    private final NexusRevivePlugin plugin;

    public GpsMenuListener(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GpsService.GpsMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        String action = meta == null ? null : meta.getPersistentDataContainer().get(GpsService.ACTION_KEY, PersistentDataType.STRING);
        if (action != null) {
            switch (action) {
                case "previous" -> player.openInventory(plugin.getGpsService().buildMenu(player, holder.page() - 1));
                case "next" -> player.openInventory(plugin.getGpsService().buildMenu(player, holder.page() + 1));
                case "close" -> player.closeInventory();
                default -> {
                }
            }
            return;
        }

        String raw = meta == null ? null : meta.getPersistentDataContainer().get(GpsService.TARGET_KEY, PersistentDataType.STRING);
        if (raw != null) {
            plugin.getGpsService().toggleTracking(player, UUID.fromString(raw));
            player.closeInventory();
        }
    }
}

