package com.aquiles.nexusrevive.nms.v1_20;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.nms.reflective.AbstractReflectiveDownedPoseAdapter;
import com.aquiles.nexusrevive.service.NmsPoseBroadcaster;

public final class V1_20DownedPoseAdapter extends AbstractReflectiveDownedPoseAdapter {
    public V1_20DownedPoseAdapter(NexusRevivePlugin plugin, NmsPoseBroadcaster broadcaster) {
        super(plugin, broadcaster, "1.20");
    }
}

