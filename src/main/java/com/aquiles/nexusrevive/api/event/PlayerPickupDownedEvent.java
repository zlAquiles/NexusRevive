package com.aquiles.nexusrevive.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public final class PlayerPickupDownedEvent extends NexusRevivePlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player picker;
    private boolean cancelled;

    public PlayerPickupDownedEvent(Player victim, Player picker) {
        super(victim);
        this.picker = picker;
    }

    public Player getPicker() {
        return picker;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

