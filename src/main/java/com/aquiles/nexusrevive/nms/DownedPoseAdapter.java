package com.aquiles.nexusrevive.nms;

import org.bukkit.entity.Player;

public interface DownedPoseAdapter {
    String versionId();

    void applyDownedPose(Player player);

    void refreshDownedPose(Player player);

    void clearDownedPose(Player player);
}

