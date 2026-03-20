package com.aquiles.nexusrevive.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PlayerReviveEvent extends NexusRevivePlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player reviver;

    public PlayerReviveEvent(Player victim, Player reviver) {
        super(victim);
        this.reviver = reviver;
    }

    public Player getReviver() {
        return reviver;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

