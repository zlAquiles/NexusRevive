package com.aquiles.nexusrevive.nms.fallback;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.nms.reflective.AbstractReflectiveDownedPoseAdapter;
import com.aquiles.nexusrevive.service.NmsPoseBroadcaster;

public final class FallbackDownedPoseAdapter extends AbstractReflectiveDownedPoseAdapter {
    public FallbackDownedPoseAdapter(NexusRevivePlugin plugin, NmsPoseBroadcaster broadcaster, String versionId) {
        super(plugin, broadcaster, versionId);
    }
}

