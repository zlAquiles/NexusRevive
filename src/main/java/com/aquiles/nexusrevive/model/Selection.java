package com.aquiles.nexusrevive.model;

import org.bukkit.Location;

public final class Selection {
    private Location pos1;
    private Location pos2;
    private boolean selectorEnabled;
    private long lastSelectionAt;

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
        touch();
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
        touch();
    }

    public boolean isSelectorEnabled() {
        return selectorEnabled;
    }

    public void setSelectorEnabled(boolean selectorEnabled) {
        this.selectorEnabled = selectorEnabled;
    }

    public long getLastSelectionAt() {
        return lastSelectionAt;
    }

    public void touch() {
        this.lastSelectionAt = System.currentTimeMillis();
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null;
    }

    public boolean hasWorldMismatch() {
        return isComplete()
                && pos1.getWorld() != null
                && pos2.getWorld() != null
                && !pos1.getWorld().equals(pos2.getWorld());
    }

    public void clear() {
        this.pos1 = null;
        this.pos2 = null;
        touch();
    }
}

