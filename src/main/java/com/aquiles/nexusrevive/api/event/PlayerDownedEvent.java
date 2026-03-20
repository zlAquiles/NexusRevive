package com.aquiles.nexusrevive.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;

public final class PlayerDownedEvent extends NexusRevivePlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player attacker;
    private final EntityDamageEvent.DamageCause cause;
    private boolean cancelled;

    public PlayerDownedEvent(Player victim, Player attacker, EntityDamageEvent.DamageCause cause) {
        super(victim);
        this.attacker = attacker;
        this.cause = cause;
    }

    public Player getAttacker() {
        return attacker;
    }

    public EntityDamageEvent.DamageCause getCause() {
        return cause;
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

