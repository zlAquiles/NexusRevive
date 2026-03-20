package com.aquiles.nexusrevive.model;

import java.util.UUID;

public final class DownedPlayer {
    private final UUID playerId;
    private UUID attackerId;
    private UUID carrierId;
    private UUID activeReviverId;
    private int secondsUntilDeath;
    private int invulnerabilitySeconds;
    private double autoReviveProgressSeconds;
    private String autoReviveZoneName;

    public DownedPlayer(UUID playerId, UUID attackerId, int secondsUntilDeath, int invulnerabilitySeconds) {
        this.playerId = playerId;
        this.attackerId = attackerId;
        this.secondsUntilDeath = secondsUntilDeath;
        this.invulnerabilitySeconds = invulnerabilitySeconds;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getAttackerId() {
        return attackerId;
    }

    public void setAttackerId(UUID attackerId) {
        this.attackerId = attackerId;
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(UUID carrierId) {
        this.carrierId = carrierId;
    }

    public UUID getActiveReviverId() {
        return activeReviverId;
    }

    public void setActiveReviverId(UUID activeReviverId) {
        this.activeReviverId = activeReviverId;
    }

    public int getSecondsUntilDeath() {
        return secondsUntilDeath;
    }

    public void setSecondsUntilDeath(int secondsUntilDeath) {
        this.secondsUntilDeath = secondsUntilDeath;
    }

    public void tickDeathTimer() {
        this.secondsUntilDeath--;
    }

    public int getInvulnerabilitySeconds() {
        return invulnerabilitySeconds;
    }

    public void setInvulnerabilitySeconds(int invulnerabilitySeconds) {
        this.invulnerabilitySeconds = invulnerabilitySeconds;
    }

    public void tickInvulnerability() {
        if (invulnerabilitySeconds > 0) {
            invulnerabilitySeconds--;
        }
    }

    public double getAutoReviveProgressSeconds() {
        return autoReviveProgressSeconds;
    }

    public void setAutoReviveProgressSeconds(double autoReviveProgressSeconds) {
        this.autoReviveProgressSeconds = Math.max(0.0D, autoReviveProgressSeconds);
    }

    public void addAutoReviveProgressSeconds(double amount) {
        this.autoReviveProgressSeconds = Math.max(0.0D, autoReviveProgressSeconds + amount);
    }

    public String getAutoReviveZoneName() {
        return autoReviveZoneName;
    }

    public void setAutoReviveZoneName(String autoReviveZoneName) {
        this.autoReviveZoneName = autoReviveZoneName;
    }

    public boolean isAutoReviving() {
        return autoReviveZoneName != null && !autoReviveZoneName.isBlank();
    }

    public void clearAutoRevive() {
        this.autoReviveProgressSeconds = 0.0D;
        this.autoReviveZoneName = null;
    }
}

