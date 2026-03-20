package com.aquiles.nexusrevive.model;

import org.bukkit.Location;
import org.bukkit.World;

public record ReviveZone(
        String name,
        String worldName,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        double speedMultiplier,
        boolean autoRevive
) {
    public boolean contains(Location location) {
        World world = location.getWorld();
        if (world == null || !world.getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}

