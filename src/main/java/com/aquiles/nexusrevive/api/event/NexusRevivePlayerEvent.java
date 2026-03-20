package com.aquiles.nexusrevive.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class NexusRevivePlayerEvent extends PlayerEvent {
    protected NexusRevivePlayerEvent(Player who) {
        super(who);
    }

    public Player getVictim() {
        return getPlayer();
    }
}

