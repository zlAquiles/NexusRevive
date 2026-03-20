package com.aquiles.nexusrevive.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PlayerDropDownedEvent extends NexusRevivePlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player picker;

    public PlayerDropDownedEvent(Player victim, Player picker) {
        super(victim);
        this.picker = picker;
    }

    public Player getPicker() {
        return picker;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

