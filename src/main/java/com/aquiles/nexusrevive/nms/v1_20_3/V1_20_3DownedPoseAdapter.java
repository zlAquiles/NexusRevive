package com.aquiles.nexusrevive.nms.v1_20_3;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.nms.reflective.AbstractReflectiveDownedPoseAdapter;
import com.aquiles.nexusrevive.service.NmsPoseBroadcaster;

public final class V1_20_3DownedPoseAdapter extends AbstractReflectiveDownedPoseAdapter {
    public V1_20_3DownedPoseAdapter(NexusRevivePlugin plugin, NmsPoseBroadcaster broadcaster) {
        super(plugin, broadcaster, "1.20.3");
    }
}

