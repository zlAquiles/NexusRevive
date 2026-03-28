package com.aquiles.nexusrevive.model;

import com.aquiles.nexusrevive.scheduler.NexusTask;

import java.util.UUID;

public final class ReviveSession {
    private final UUID reviverId;
    private final UUID victimId;
    private final double requiredSeconds;
    private NexusTask task;
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

    public NexusTask getTask() {
        return task;
    }

    public void setTask(NexusTask task) {
        this.task = task;
    }
}

