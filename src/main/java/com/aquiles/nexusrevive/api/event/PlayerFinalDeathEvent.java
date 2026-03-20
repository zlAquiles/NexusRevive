package com.aquiles.nexusrevive.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PlayerFinalDeathEvent extends NexusRevivePlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player attacker;

    public PlayerFinalDeathEvent(Player victim, Player attacker) {
        super(victim);
        this.attacker = attacker;
    }

    public Player getAttacker() {
        return attacker;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

