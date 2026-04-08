package com.aquiles.nexusrevive.listener;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.config.PluginSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class PlayerStateListener implements Listener {
    private final NexusRevivePlugin plugin;

    public PlayerStateListener(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDownedService().isDowned(player)) {
            if (event.isSneaking()) {
                plugin.getDownedService().startSuicide(player);
            } else {
                plugin.getDownedService().cancelSuicide(player);
            }
            return;
        }

        if (!event.isSneaking() && plugin.getPluginSettings().revive().requireSneak()) {
            plugin.getDownedService().stopRevive(player, true);
            return;
        }

        if (event.isSneaking()) {
            if (plugin.getDownedService().isCarryingDowned(player)) {
                plugin.getDownedService().dropCarried(player);
                return;
            }
            plugin.getDownedService().findNearestDowned(player)
                    .ifPresent(victim -> plugin.getDownedService().startRevive(player, victim));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.getDownedService().isDowned(event.getPlayer())
                && !plugin.getPluginSettings().downedInteractions().allowEntityInteract()) {
            event.setCancelled(true);
            return;
        }
        if (event.getRightClicked() instanceof Player target) {
            if (plugin.getLootService().handleLootInteract(event.getPlayer(), target)) {
                event.setCancelled(true);
                return;
            }
            if (plugin.getDownedService().pickUp(event.getPlayer(), target)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player picker && plugin.getPluginSettings().carry().dropOnPickerDamage()) {
            plugin.getDownedService().dropCarried(picker);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onToggleSwim(EntityToggleSwimEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getDownedService().isDowned(player)) {
            if (!event.isSwimming()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player victim
                && plugin.getDownedService().isDowned(victim)
                && !plugin.getDownedService().isForcedDismount(victim)
                && !plugin.getPluginSettings().carry().allowDownedDismount()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer()) && event.getTo() != null
                && (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ())) {
            plugin.getDownedService().refreshPose(event.getPlayer());
        }

        if (!plugin.getDownedService().isDowned(event.getPlayer()) || plugin.getPluginSettings().downedInteractions().allowMove()) {
            return;
        }
        if (event.getTo() != null
                && (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer())
                && isArmorEquipInteractBlocked(event, plugin.getPluginSettings().downedInteractions())) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getDownedService().isDowned(event.getPlayer()) && !plugin.getPluginSettings().downedInteractions().allowInteract()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer()) && !plugin.getPluginSettings().downedInteractions().allowBlockBreak()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer()) && !plugin.getPluginSettings().downedInteractions().allowBlockPlace()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer()) && !plugin.getPluginSettings().downedInteractions().allowTeleport()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer()) && !plugin.getPluginSettings().downedInteractions().allowItemDrop()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getDownedService().isDowned(player)
                && !plugin.getPluginSettings().downedInteractions().allowItemPickup()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer())
                && !plugin.getPluginSettings().downedInteractions().allowConsume()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !plugin.getDownedService().isDowned(player)) {
            return;
        }
        if (shouldCancelInventoryClick(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !plugin.getDownedService().isDowned(player)) {
            return;
        }

        PluginSettings.DownedInteractions interactions = plugin.getPluginSettings().downedInteractions();
        for (int rawSlot : event.getRawSlots()) {
            if (isCraftingRawSlot(event.getView().getType(), rawSlot) && !interactions.allowCraftingGrid()) {
                event.setCancelled(true);
                return;
            }

            int playerSlot = translatePlayerSlot(event.getView().getType(), event.getView().getTopInventory().getSize(), rawSlot);
            if (playerSlot != -1 && !isPlayerSlotAllowed(playerSlot, interactions)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && plugin.getDownedService().isDowned(player)
                && !plugin.getPluginSettings().downedInteractions().allowCraftingGrid()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    @SuppressWarnings("removal")
    public void onRecipeBookClick(PlayerRecipeBookClickEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDownedService().isDowned(player)
                || plugin.getPluginSettings().downedInteractions().allowRecipeBook()) {
            return;
        }

        plugin.getSchedulerFacade().runEntityLater(player, () -> revertRecipeBookCraft(player), () -> {
        }, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer())
                && !plugin.getPluginSettings().downedInteractions().allowOffhand()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getDownedService().isDowned(event.getPlayer())) {
            return;
        }
        String raw = event.getMessage().trim().toLowerCase();
        String withoutSlash = raw.startsWith("/") ? raw.substring(1) : raw;
        if (withoutSlash.isBlank()) {
            return;
        }

        String commandLabel = withoutSlash.split("\\s+")[0];
        if (commandLabel.equals("nexusrevive") || commandLabel.equals("nr")) {
            return;
        }
        PluginSettings.Commands commands = plugin.getPluginSettings().commands();
        boolean listed = commands.list().contains(commandLabel);
        boolean allowed = commands.mode() == PluginSettings.ListMode.WHITELIST ? listed : !listed;

        if (allowed) {
            return;
        }

        event.setCancelled(true);
        plugin.getMessages().send(event.getPlayer(), "general.blocked-command");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGpsService().stopTracking(event.getPlayer());
        plugin.getScoreboardService().clear(event.getPlayer());
        plugin.getDownedService().handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getGpsService().handleViewerJoin(event.getPlayer());
        plugin.getScoreboardService().handleJoin(event.getPlayer());
        plugin.getDownedService().handleReconnect(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (plugin.getDownedService().isDowned(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlight(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getDownedService().isDowned(player)
                && !plugin.getPluginSettings().downedInteractions().allowGliding()) {
            event.setCancelled(true);
        }
    }

    private boolean shouldCancelInventoryClick(InventoryClickEvent event) {
        PluginSettings.DownedInteractions interactions = plugin.getPluginSettings().downedInteractions();

        if (event.getClick() == ClickType.DOUBLE_CLICK && !interactions.allowInventory()) {
            return true;
        }

        if (isCraftingRawSlot(event.getView().getType(), event.getRawSlot()) && !interactions.allowCraftingGrid()) {
            return true;
        }

        if (event.getClick() == ClickType.SWAP_OFFHAND && !interactions.allowOffhand()) {
            return true;
        }

        if (event.getClick() == ClickType.NUMBER_KEY && !interactions.allowInventory()) {
            return true;
        }

        int playerSlot = resolveClickedPlayerSlot(event);
        if (playerSlot != -1) {
            return !isPlayerSlotAllowed(playerSlot, interactions);
        }

        return event.isShiftClick() && wouldShiftIntoBlockedSlot(event.getCurrentItem(), interactions);
    }

    private boolean isCraftingRawSlot(InventoryType viewType, int rawSlot) {
        return viewType == InventoryType.CRAFTING && rawSlot >= 0 && rawSlot <= 4;
    }

    private boolean isArmorEquipInteractBlocked(PlayerInteractEvent event, PluginSettings.DownedInteractions interactions) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return false;
        }

        EquipmentSlot slot = item.getType().getEquipmentSlot();
        return isArmorEquipmentSlot(slot) && !isEquipmentSlotAllowed(slot, interactions);
    }

    private boolean isArmorEquipmentSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET;
    }

    private void revertRecipeBookCraft(Player player) {
        if (!player.isOnline()
                || !plugin.getDownedService().isDowned(player)
                || plugin.getPluginSettings().downedInteractions().allowRecipeBook()
                || player.getOpenInventory().getType() != InventoryType.CRAFTING) {
            return;
        }

        var craftingInventory = player.getOpenInventory().getTopInventory();
        craftingInventory.setItem(0, null);
        for (int slot = 1; slot <= 4; slot++) {
            ItemStack ingredient = craftingInventory.getItem(slot);
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }

            craftingInventory.setItem(slot, null);
            var leftovers = player.getInventory().addItem(ingredient);
            for (ItemStack leftover : leftovers.values()) {
                if (leftover == null || leftover.getType().isAir()) {
                    continue;
                }
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        player.updateInventory();
    }

    private int resolveClickedPlayerSlot(InventoryClickEvent event) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return event.getSlot();
        }

        return translatePlayerSlot(event.getView().getType(), event.getView().getTopInventory().getSize(), event.getRawSlot());
    }

    private int translatePlayerSlot(InventoryType viewType, int topSize, int rawSlot) {
        if (viewType == InventoryType.CRAFTING) {
            return switch (rawSlot) {
                case 5 -> 39;
                case 6 -> 38;
                case 7 -> 37;
                case 8 -> 36;
                case 45 -> 40;
                default -> rawSlot >= topSize && rawSlot < topSize + 36 ? rawSlot - topSize : -1;
            };
        }

        if (rawSlot >= topSize && rawSlot < topSize + 36) {
            return rawSlot - topSize;
        }
        return -1;
    }

    private boolean isPlayerSlotAllowed(int slot, PluginSettings.DownedInteractions interactions) {
        return switch (slot) {
            case 40 -> interactions.allowOffhand();
            case 39 -> interactions.allowHelmet();
            case 38 -> interactions.allowChestplate();
            case 37 -> interactions.allowLeggings();
            case 36 -> interactions.allowBoots();
            default -> interactions.allowInventory();
        };
    }

    private boolean wouldShiftIntoBlockedSlot(ItemStack item, PluginSettings.DownedInteractions interactions) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        if (!interactions.allowInventory()) {
            return true;
        }

        EquipmentSlot equipmentSlot = item.getType().getEquipmentSlot();
        return isArmorEquipmentSlot(equipmentSlot) && !isEquipmentSlotAllowed(equipmentSlot, interactions);
    }

    private boolean isEquipmentSlotAllowed(EquipmentSlot slot, PluginSettings.DownedInteractions interactions) {
        return switch (slot) {
            case HEAD -> interactions.allowHelmet();
            case CHEST -> interactions.allowChestplate();
            case LEGS -> interactions.allowLeggings();
            case FEET -> interactions.allowBoots();
            case OFF_HAND -> interactions.allowOffhand();
            default -> interactions.allowInventory();
        };
    }
}

