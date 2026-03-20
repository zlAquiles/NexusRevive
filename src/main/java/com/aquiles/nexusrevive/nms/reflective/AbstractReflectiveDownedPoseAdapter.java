package com.aquiles.nexusrevive.nms.reflective;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.nms.DownedPoseAdapter;
import com.aquiles.nexusrevive.service.NmsPoseBroadcaster;
import org.bukkit.entity.Player;

public abstract class AbstractReflectiveDownedPoseAdapter implements DownedPoseAdapter {
    protected final NexusRevivePlugin plugin;
    protected final NmsPoseBroadcaster broadcaster;
    private final String versionId;

    protected AbstractReflectiveDownedPoseAdapter(NexusRevivePlugin plugin, NmsPoseBroadcaster broadcaster, String versionId) {
        this.plugin = plugin;
        this.broadcaster = broadcaster;
        this.versionId = versionId;
    }

    @Override
    public String versionId() {
        return versionId;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void applyDownedPose(Player player) {
        player.setSwimming(true);
        broadcaster.broadcastCurrentMetadata(player);
    }

    @Override
    public void refreshDownedPose(Player player) {
        broadcaster.broadcastCurrentMetadata(player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void clearDownedPose(Player player) {
        player.setSwimming(false);
        broadcaster.broadcastCurrentMetadata(player);
    }
}

