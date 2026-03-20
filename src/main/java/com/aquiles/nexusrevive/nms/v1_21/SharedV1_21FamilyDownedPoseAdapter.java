package com.aquiles.nexusrevive.nms.v1_21;

import com.aquiles.nexusrevive.nms.DownedPoseAdapter;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Set;

public abstract class SharedV1_21FamilyDownedPoseAdapter implements DownedPoseAdapter {
    @Override
    public void applyDownedPose(Player player) {
        player.setSwimming(true);
        syncPoseForViewers(player, Pose.SWIMMING);
    }

    @Override
    public void refreshDownedPose(Player player) {
        if (!player.isSwimming()) {
            player.setSwimming(true);
        }
        syncPoseForViewers(player, Pose.SWIMMING);
    }

    @Override
    public void clearDownedPose(Player player) {
        player.setSwimming(false);
        syncPoseForViewers(player, Pose.STANDING);
    }

    protected void syncPoseForViewers(Player player, Pose pose) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }

        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.setPose(pose);
        handle.refreshDimensions();

        var trackedValues = handle.getEntityData().isDirty()
                ? handle.getEntityData().packDirty()
                : handle.getEntityData().getNonDefaultValues();

        if (trackedValues == null || trackedValues.isEmpty()) {
            trackedValues = handle.getEntityData().getNonDefaultValues();
        }
        if (trackedValues == null || trackedValues.isEmpty()) {
            return;
        }

        ClientboundSetEntityDataPacket metadataPacket = new ClientboundSetEntityDataPacket(handle.getId(), trackedValues);
        ClientboundTeleportEntityPacket teleportPacket = new ClientboundTeleportEntityPacket(
                handle.getId(),
                PositionMoveRotation.of(handle),
                Set.of(),
                false
        );

        for (Player viewer : player.getTrackedPlayers()) {
            if (viewer.equals(player)) {
                continue;
            }
            sendPacket(viewer, metadataPacket);
            sendPacket(viewer, teleportPacket);
        }
    }

    protected void sendPacket(Player viewer, Packet<?> packet) {
        if (viewer instanceof CraftPlayer craftPlayer) {
            craftPlayer.getHandle().connection.send(packet);
        }
    }
}
