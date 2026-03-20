package com.aquiles.nexusrevive.nms;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.nms.fallback.FallbackDownedPoseAdapter;
import com.aquiles.nexusrevive.nms.v1_21.V1_21DownedPoseAdapter;
import com.aquiles.nexusrevive.nms.v1_21_11.V1_21_11DownedPoseAdapter;
import com.aquiles.nexusrevive.nms.v1_21_2.V1_21_2DownedPoseAdapter;
import com.aquiles.nexusrevive.nms.v1_21_4.V1_21_4DownedPoseAdapter;
import com.aquiles.nexusrevive.nms.v1_21_5.V1_21_5DownedPoseAdapter;
import com.aquiles.nexusrevive.nms.v1_21_6.V1_21_6DownedPoseAdapter;
import com.aquiles.nexusrevive.nms.v1_21_9.V1_21_9DownedPoseAdapter;
import com.aquiles.nexusrevive.service.NmsPoseBroadcaster;

public final class NmsAdapterResolver {
    private NmsAdapterResolver() {
    }

    public static DownedPoseAdapter resolve(NexusRevivePlugin plugin, String minecraftVersion, NmsPoseBroadcaster broadcaster) {
        return switch (minecraftVersion) {
            case "1.21", "1.21.1" -> new V1_21DownedPoseAdapter(plugin, broadcaster);
            case "1.21.2", "1.21.3" -> new V1_21_2DownedPoseAdapter(plugin, broadcaster);
            case "1.21.4" -> new V1_21_4DownedPoseAdapter(plugin, broadcaster);
            case "1.21.5" -> new V1_21_5DownedPoseAdapter(plugin, broadcaster);
            case "1.21.6", "1.21.7", "1.21.8" -> new V1_21_6DownedPoseAdapter(plugin, broadcaster);
            case "1.21.9", "1.21.10" -> new V1_21_9DownedPoseAdapter(plugin, broadcaster);
            case "1.21.11" -> new V1_21_11DownedPoseAdapter(plugin, broadcaster);
            default -> {
                plugin.getLogger().warning("NexusRevive esta afinado ahora para la familia 1.21.x. Version detectada: " + minecraftVersion + ". Se usara fallback temporal.");
                yield new FallbackDownedPoseAdapter(plugin, broadcaster, minecraftVersion);
            }
        };
    }
}

