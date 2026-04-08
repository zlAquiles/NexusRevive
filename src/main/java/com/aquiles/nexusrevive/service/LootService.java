package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.util.Components;
import com.aquiles.nexusrevive.util.PermissionNodes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LootService {
    private final NexusRevivePlugin plugin;
    private final Map<UUID, LootSession> sessionsByRobber = new HashMap<>();
    private final Map<UUID, UUID> robberByVictim = new HashMap<>();

    public LootService(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        closeAll(false, null);
    }

    public void shutdown() {
        closeAll(false, null);
    }

    public boolean handleLootInteract(Player robber, Player victim) {
        debug("Interact robber=" + robber.getName() + " victim=" + victim.getName()
                + " downed=" + plugin.getDownedService().isDowned(victim)
                + " sneaking=" + robber.isSneaking());
        if (!plugin.getPluginSettings().loot().enabled()
                || !plugin.getDownedService().isDowned(victim)
                || (plugin.getPluginSettings().loot().requireSneak() && !robber.isSneaking())) {
            debug("Interact blocked by base conditions.");
            return false;
        }

        if (robber.equals(victim)) {
            debug("Interact blocked: robber is victim.");
            plugin.getMessages().send(robber, "loot.cannot-loot-self");
            return true;
        }
        if (!robber.hasPermission(PermissionNodes.ROBBER)) {
            debug("Interact blocked: robber missing permission.");
            plugin.getMessages().send(robber, "general.no-permission");
            return true;
        }
        if (!victim.hasPermission(PermissionNodes.LOOTABLE)) {
            debug("Interact blocked: victim missing lootable permission.");
            plugin.getMessages().send(robber, "general.no-permission");
            return true;
        }
        CompatibilityService.BlockReason lootReason = plugin.getCompatibilityService().canLoot(robber, victim);
        if (lootReason != CompatibilityService.BlockReason.NONE) {
            debug("Interact blocked: compatibility block reason=" + lootReason);
            plugin.getMessages().send(robber, switch (lootReason) {
                case REGION_BLOCKED -> "hooks.region-blocked";
                case IN_COMBAT -> "hooks.in-combat";
                case NO_MONEY -> "hooks.not-enough-money";
                case TARGET_HIDDEN -> "hooks.target-hidden";
                default -> "general.no-permission";
            });
            return true;
        }
        if (!plugin.getPluginSettings().loot().allowWhileReviving() && plugin.getDownedService().isBeingRevived(victim)) {
            debug("Interact blocked: victim currently being revived.");
            plugin.getMessages().send(robber, "loot.blocked-reviving");
            return true;
        }
        if (!plugin.getPluginSettings().loot().allowWhileCarried()
                && plugin.getDownedService().getDownedState(victim).map(state -> state.getCarrierId() != null).orElse(false)) {
            debug("Interact blocked: victim currently carried.");
            plugin.getMessages().send(robber, "loot.blocked-carried");
            return true;
        }

        LootSession existingForRobber = sessionsByRobber.get(robber.getUniqueId());
        if (existingForRobber != null && existingForRobber.victimId().equals(victim.getUniqueId())) {
            debug("Re-opening existing session for robber=" + robber.getName());
            render(existingForRobber);
            robber.openInventory(existingForRobber.inventory());
            return true;
        }
        if (existingForRobber != null) {
            debug("Closing previous session for robber=" + robber.getName());
            closeByRobber(robber, false, null);
        }

        UUID currentRobber = robberByVictim.get(victim.getUniqueId());
        if (plugin.getPluginSettings().loot().singleRobberLock()
                && currentRobber != null
                && !currentRobber.equals(robber.getUniqueId())) {
            debug("Interact blocked: victim already being looted by another player.");
            plugin.getMessages().send(robber, "loot.busy");
            return true;
        }

        LootSession session = createSession(robber, victim);
        sessionsByRobber.put(robber.getUniqueId(), session);
        robberByVictim.put(victim.getUniqueId(), robber.getUniqueId());
        render(session);
        robber.openInventory(session.inventory());
        debug("Session opened robber=" + robber.getName() + " victim=" + victim.getName());
        plugin.getMessages().send(robber, "loot.opened", Map.of("victim", victim.getName()));
        return true;
    }

    public boolean isLootInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof LootHolder;
    }

    public void handleLootClick(Player robber, int rawSlot, ClickType clickType) {
        LootSession session = sessionsByRobber.get(robber.getUniqueId());
        if (session == null) {
            debug("Click ignored: no session for robber=" + robber.getName());
            return;
        }

        Inventory inventory = robber.getOpenInventory().getTopInventory();
        if (!(inventory.getHolder() instanceof LootHolder holder) || !holder.victimId().equals(session.victimId())) {
            debug("Click ignored: open inventory holder mismatch for robber=" + robber.getName()
                    + " holder=" + (inventory.getHolder() == null ? "null" : inventory.getHolder().getClass().getSimpleName()));
            return;
        }
        if (rawSlot < 0 || rawSlot >= inventory.getSize()) {
            debug("Click ignored: rawSlot outside top inventory robber=" + robber.getName() + " rawSlot=" + rawSlot);
            return;
        }
        if (!clickType.isLeftClick() && !clickType.isRightClick() && !clickType.isShiftClick()) {
            debug("Click ignored: unsupported clickType=" + clickType + " robber=" + robber.getName());
            return;
        }
        debug("Click accepted robber=" + robber.getName() + " rawSlot=" + rawSlot + " clickType=" + clickType);

        Player victim = Bukkit.getPlayer(session.victimId());
        if (victim == null || !victim.isOnline() || !plugin.getDownedService().isDowned(victim)) {
            debug("Click closing: victim unavailable/downed state lost victimId=" + session.victimId());
            closeByVictimId(session.victimId(), true, "loot.target-closed");
            return;
        }

        int victimSlot = guiToVictimSlot(rawSlot);
        if (victimSlot < 0 || !isStealableSlot(victimSlot)) {
            debug("Click ignored: mapped victimSlot=" + victimSlot + " stealable=" + (victimSlot >= 0 && isStealableSlot(victimSlot)));
            return;
        }
        debug("Click mapped robber=" + robber.getName() + " rawSlot=" + rawSlot + " -> victimSlot=" + victimSlot);

        ItemStack stack = getVictimItem(victim, victimSlot);
        if (stack == null || stack.getType().isAir()) {
            debug("Click found empty slot victim=" + victim.getName() + " victimSlot=" + victimSlot);
            return;
        }
        debug("Click found item victim=" + victim.getName() + " victimSlot=" + victimSlot
                + " item=" + stack.getType() + " amount=" + stack.getAmount());
        boolean changed = switch (clickType) {
            case SHIFT_LEFT, SHIFT_RIGHT -> handleShiftLoot(robber, victim, victimSlot, stack);
            case LEFT -> handleLeftLoot(robber, victim, victimSlot, stack);
            case RIGHT -> handleRightLoot(robber, victim, victimSlot, stack);
            default -> false;
        };
        if (changed) {
            debug("Click transfer success robber=" + robber.getName() + " victim=" + victim.getName()
                    + " victimSlot=" + victimSlot + " item=" + stack.getType() + " amount=" + stack.getAmount());
            victim.updateInventory();
            robber.updateInventory();
            render(session);
        }
    }

    public void handleLootClose(Player robber) {
        closeByRobber(robber, false, null);
    }

    public void closeByVictim(Player victim, boolean notify, String messagePath) {
        closeByVictimId(victim.getUniqueId(), notify, messagePath);
    }

    public void closeForPlayer(Player player) {
        closeByRobber(player, false, null);
        closeByVictim(player, false, null);
    }

    private LootSession createSession(Player robber, Player victim) {
        String title = plugin.getMessages().text("loot.title", Map.of("victim", victim.getName()));
        LootHolder holder = new LootHolder(victim.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, Components.colorize(title));
        holder.setInventory(inventory);
        return new LootSession(robber.getUniqueId(), victim.getUniqueId(), inventory);
    }

    private void render(LootSession session) {
        Player victim = Bukkit.getPlayer(session.victimId());
        if (victim == null) {
            return;
        }

        Inventory inventory = session.inventory();
        clearDecorations(inventory);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int victimSlot = guiToVictimSlot(slot);
            if (victimSlot < 0) {
                continue;
            }

            if (!isStealableSlot(victimSlot)) {
                inventory.setItem(slot, lockedItem());
                continue;
            }

            ItemStack victimItem = getVictimItem(victim, victimSlot);
            inventory.setItem(slot, victimItem == null ? null : victimItem.clone());
        }

        inventory.setItem(49, victimHead(victim));
    }

    private void clearDecorations(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int victimSlot = guiToVictimSlot(slot);
            if (victimSlot < 0) {
                inventory.setItem(slot, filler());
            } else {
                inventory.setItem(slot, null);
            }
        }
    }

    private int guiToVictimSlot(int guiSlot) {
        if (guiSlot >= 9 && guiSlot <= 35) {
            return guiSlot;
        }
        if (guiSlot >= 36 && guiSlot <= 44) {
            return guiSlot - 36;
        }
        return switch (guiSlot) {
            case 0 -> 39;
            case 1 -> 38;
            case 2 -> 37;
            case 3 -> 36;
            case 4 -> 40;
            default -> -1;
        };
    }

    private boolean isStealableSlot(int victimSlot) {
        if (victimSlot >= 0 && victimSlot <= 35) {
            return plugin.getPluginSettings().loot().allowMainInventory();
        }
        if (victimSlot >= 36 && victimSlot <= 39) {
            return plugin.getPluginSettings().loot().allowArmor();
        }
        return victimSlot == 40 && plugin.getPluginSettings().loot().allowOffhand();
    }

    private ItemStack getVictimItem(Player victim, int slot) {
        return victim.getInventory().getItem(slot);
    }

    private void setVictimItem(Player victim, int slot, ItemStack stack) {
        victim.getInventory().setItem(slot, stack);
    }

    private boolean canFitCompletely(PlayerInventory inventory, ItemStack stack) {
        int remaining = stack.getAmount();
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) {
                remaining -= stack.getMaxStackSize();
            } else if (current.isSimilar(stack)) {
                remaining -= Math.max(0, current.getMaxStackSize() - current.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean handleShiftLoot(Player robber, Player victim, int victimSlot, ItemStack stack) {
        Map<Integer, ItemStack> leftovers = robber.getInventory().addItem(stack.clone());
        int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (leftoverAmount >= stack.getAmount()) {
            debug("Shift loot blocked: robber inventory lacks space robber=" + robber.getName());
            plugin.getMessages().send(robber, "loot.no-space");
            return false;
        }

        if (leftoverAmount <= 0) {
            setVictimItem(victim, victimSlot, null);
        } else {
            ItemStack remaining = stack.clone();
            remaining.setAmount(leftoverAmount);
            setVictimItem(victim, victimSlot, remaining);
        }
        return true;
    }

    private boolean handleLeftLoot(Player robber, Player victim, int victimSlot, ItemStack stack) {
        ItemStack cursor = sanitize(robber.getItemOnCursor());
        if (cursor == null) {
            robber.setItemOnCursor(stack.clone());
            setVictimItem(victim, victimSlot, null);
            return true;
        }

        if (!cursor.isSimilar(stack)) {
            debug("Left loot ignored: cursor mismatch robber=" + robber.getName());
            return false;
        }

        int maxStack = cursor.getMaxStackSize();
        int transfer = Math.min(Math.max(0, maxStack - cursor.getAmount()), stack.getAmount());
        if (transfer <= 0) {
            debug("Left loot ignored: cursor full robber=" + robber.getName());
            return false;
        }

        ItemStack mergedCursor = cursor.clone();
        mergedCursor.setAmount(cursor.getAmount() + transfer);
        robber.setItemOnCursor(mergedCursor);

        if (transfer >= stack.getAmount()) {
            setVictimItem(victim, victimSlot, null);
        } else {
            ItemStack remaining = stack.clone();
            remaining.setAmount(stack.getAmount() - transfer);
            setVictimItem(victim, victimSlot, remaining);
        }
        return true;
    }

    private boolean handleRightLoot(Player robber, Player victim, int victimSlot, ItemStack stack) {
        ItemStack cursor = sanitize(robber.getItemOnCursor());
        if (cursor == null) {
            int take = (stack.getAmount() + 1) / 2;
            ItemStack taken = stack.clone();
            taken.setAmount(take);
            robber.setItemOnCursor(taken);

            if (take >= stack.getAmount()) {
                setVictimItem(victim, victimSlot, null);
            } else {
                ItemStack remaining = stack.clone();
                remaining.setAmount(stack.getAmount() - take);
                setVictimItem(victim, victimSlot, remaining);
            }
            return true;
        }

        if (!cursor.isSimilar(stack) || cursor.getAmount() >= cursor.getMaxStackSize()) {
            debug("Right loot ignored: cursor mismatch/full robber=" + robber.getName());
            return false;
        }

        ItemStack mergedCursor = cursor.clone();
        mergedCursor.setAmount(cursor.getAmount() + 1);
        robber.setItemOnCursor(mergedCursor);

        if (stack.getAmount() <= 1) {
            setVictimItem(victim, victimSlot, null);
        } else {
            ItemStack remaining = stack.clone();
            remaining.setAmount(stack.getAmount() - 1);
            setVictimItem(victim, victimSlot, remaining);
        }
        return true;
    }

    private ItemStack sanitize(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || itemStack.getAmount() <= 0) {
            return null;
        }
        return itemStack.clone();
    }

    private void closeByVictimId(UUID victimId, boolean notify, String messagePath) {
        UUID robberId = robberByVictim.remove(victimId);
        if (robberId == null) {
            return;
        }
        debug("Closing by victim victimId=" + victimId + " robberId=" + robberId + " notify=" + notify + " message=" + messagePath);
        LootSession session = sessionsByRobber.remove(robberId);
        if (session == null) {
            return;
        }
        Player robber = Bukkit.getPlayer(robberId);
        if (robber != null && robber.isOnline()) {
            if (notify && messagePath != null) {
                plugin.getMessages().send(robber, messagePath);
            }
            if (robber.getOpenInventory().getTopInventory().getHolder() instanceof LootHolder) {
                robber.closeInventory();
            }
        }
    }

    private void closeByRobber(Player robber, boolean notify, String messagePath) {
        LootSession session = sessionsByRobber.remove(robber.getUniqueId());
        if (session == null) {
            return;
        }
        debug("Closing by robber robber=" + robber.getName() + " victimId=" + session.victimId()
                + " notify=" + notify + " message=" + messagePath);
        robberByVictim.remove(session.victimId(), robber.getUniqueId());
        if (notify && messagePath != null) {
            plugin.getMessages().send(robber, messagePath);
        }
        if (robber.isOnline() && robber.getOpenInventory().getTopInventory().getHolder() instanceof LootHolder) {
            robber.closeInventory();
        }
    }

    private void closeAll(boolean notify, String messagePath) {
        for (UUID robberId : sessionsByRobber.keySet().stream().toList()) {
            Player robber = Bukkit.getPlayer(robberId);
            if (robber != null) {
                closeByRobber(robber, notify, messagePath);
            }
        }
        sessionsByRobber.clear();
        robberByVictim.clear();
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    private ItemStack lockedItem() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Components.colorize("&cBloqueado por config")));
        return item;
    }

    private ItemStack victimHead(Player victim) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(meta -> {
            if (meta instanceof SkullMeta skullMeta) {
                Map<String, String> placeholders = Map.of(
                        "victim", victim.getName(),
                        "world", victim.getWorld().getName(),
                        "x", Integer.toString(victim.getLocation().getBlockX()),
                        "y", Integer.toString(victim.getLocation().getBlockY()),
                        "z", Integer.toString(victim.getLocation().getBlockZ())
                );
                OfflinePlayer owner = Bukkit.getOfflinePlayer(victim.getUniqueId());
                skullMeta.setOwningPlayer(owner);
                skullMeta.displayName(plugin.getMessages().component("loot.head-title", placeholders));
                skullMeta.lore(plugin.getMessages().components("loot.head-lore", placeholders));
            }
        });
        return item;
    }

    public static final class LootHolder implements InventoryHolder {
        private final UUID victimId;
        private Inventory inventory;

        public LootHolder(UUID victimId) {
            this.victimId = victimId;
        }

        public UUID victimId() {
            return victimId;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record LootSession(UUID robberId, UUID victimId, Inventory inventory) {
    }

    public void debug(String message) {
        if (plugin.getPluginSettings().loot().debug()) {
            plugin.getLogger().info("[LootDebug] " + message);
        }
    }
}

