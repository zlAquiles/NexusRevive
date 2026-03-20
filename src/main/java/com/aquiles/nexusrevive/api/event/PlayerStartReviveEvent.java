package com.aquiles.nexusrevive.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public final class PlayerStartReviveEvent extends NexusRevivePlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player reviver;
    private boolean cancelled;

    public PlayerStartReviveEvent(Player victim, Player reviver) {
        super(victim);
        this.reviver = reviver;
    }

    public Player getReviver() {
        return reviver;
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

