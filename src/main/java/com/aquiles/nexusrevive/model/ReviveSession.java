package com.aquiles.nexusrevive.model;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public final class ReviveSession {
    private final UUID reviverId;
    private final UUID victimId;
    private final double requiredSeconds;
    private BukkitTask task;
    private double progressSeconds;

    public ReviveSession(UUID reviverId, UUID victimId, double requiredSeconds) {
        this.reviverId = reviverId;
        this.victimId = victimId;
        this.requiredSeconds = requiredSeconds;
    }

    public UUID getReviverId() {
        return reviverId;
    }

    public UUID getVictimId() {
        return victimId;
    }

    public double getRequiredSeconds() {
        return requiredSeconds;
    }

    public double getProgressSeconds() {
        return progressSeconds;
    }

    public void addProgress(double value) {
        this.progressSeconds += value;
    }

    public BukkitTask getTask() {
        return task;
    }

    public void setTask(BukkitTask task) {
        this.task = task;
    }
}

