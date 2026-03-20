package com.aquiles.nexusrevive.nms.v1_19_4;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.nms.reflective.AbstractReflectiveDownedPoseAdapter;
import com.aquiles.nexusrevive.service.NmsPoseBroadcaster;

public final class V1_19_4DownedPoseAdapter extends AbstractReflectiveDownedPoseAdapter {
    public V1_19_4DownedPoseAdapter(NexusRevivePlugin plugin, NmsPoseBroadcaster broadcaster) {
        super(plugin, broadcaster, "1.19.4");
    }
}

