package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NmsPoseBroadcaster {
    private final NexusRevivePlugin plugin;
    private final Map<UUID, ClientBoxState> crawlBoxes = new HashMap<>();

    public NmsPoseBroadcaster(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    public void broadcastCurrentMetadata(Player target) {
        try {
            Object packet = createMetadataPacket(target);
            if (packet == null) {
                return;
            }

            for (Player viewer : viewersFor(target)) {
                sendPacket(viewer, packet);
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not broadcast pose metadata for " + target.getName() + ": " + exception.getMessage());
        }
    }

    public void syncCrawlBox(Player target) {
        try {
            if (hasSolidBlockAbove(target.getLocation())) {
                clearCrawlBox(target);
                return;
            }

            ClientBoxState state = crawlBoxes.computeIfAbsent(target.getUniqueId(), uuid -> {
                try {
                    return createClientBox(target.getLocation());
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            updateClientBox(state.entity(), target);

            Set<UUID> currentViewerIds = new HashSet<>();
            for (Player viewer : viewersFor(target)) {
                if (viewer.equals(target)) {
                    continue;
                }
                currentViewerIds.add(viewer.getUniqueId());

                if (state.spawnedViewers().add(viewer.getUniqueId())) {
                    sendPacket(viewer, createAddEntityPacket(state.entity()));
                }

                Object metadataPacket = createEntityMetadataPacket(state.entity());
                if (metadataPacket != null) {
                    sendPacket(viewer, metadataPacket);
                }
                sendPacket(viewer, createTeleportEntityPacket(state.entity()));
            }

            for (UUID viewerId : new HashSet<>(state.spawnedViewers())) {
                if (currentViewerIds.contains(viewerId)) {
                    continue;
                }
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) {
                    sendPacket(viewer, createRemoveEntityPacket(state.entityId()));
                }
                state.spawnedViewers().remove(viewerId);
            }
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof ReflectiveOperationException reflectiveOperationException) {
                plugin.getLogger().warning("Could not sync crawl box for " + target.getName() + ": " + reflectiveOperationException.getMessage());
            } else {
                plugin.getLogger().warning("Could not sync crawl box for " + target.getName() + ": " + exception.getMessage());
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not sync crawl box for " + target.getName() + ": " + exception.getMessage());
        }
    }

    public void clearCrawlBox(Player target) {
        ClientBoxState state = crawlBoxes.remove(target.getUniqueId());
        if (state == null) {
            return;
        }

        try {
            Object removePacket = createRemoveEntityPacket(state.entityId());
            for (UUID viewerId : state.spawnedViewers()) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) {
                    sendPacket(viewer, removePacket);
                }
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not clear crawl box for " + target.getName() + ": " + exception.getMessage());
        }
    }

    public void forceSwimmingPose(Player target) {
        try {
            forcePose(target, "SWIMMING");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not force swimming pose for " + target.getName() + ": " + exception.getMessage());
        }
    }

    public void clearForcedPose(Player target) {
        try {
            forcePose(target, "STANDING");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not clear forced pose for " + target.getName() + ": " + exception.getMessage());
        }
    }

    private List<Player> viewersFor(Player target) {
        List<Player> viewers = new ArrayList<>();
        for (Player viewer : target.getWorld().getPlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            if (!viewer.canSee(target)) {
                continue;
            }
            viewers.add(viewer);
        }
        return viewers;
    }

    private Object createMetadataPacket(Player target) throws ReflectiveOperationException {
        Object handle = getHandle(target);
        Object dataTracker = getDataTracker(handle);
        if (dataTracker == null) {
            return null;
        }

        Class<?> packetClass = nmsClass(
                "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket",
                "net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata"
        );
        List<?> trackedValues = trackedValues(dataTracker);

        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            Object packet = tryCreatePacket(constructor, target.getEntityId(), dataTracker, trackedValues);
            if (packet != null) {
                return packet;
            }
        }

        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void forcePose(Player target, String poseName) throws ReflectiveOperationException {
        Object handle = getHandle(target);
        Class<?> poseClass = nmsClass("net.minecraft.world.entity.Pose");
        Object pose = Enum.valueOf((Class<? extends Enum>) poseClass.asSubclass(Enum.class), poseName);

        for (Method method : handle.getClass().getMethods()) {
            if (!method.getName().equals("setPose") || method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(poseClass)) {
                continue;
            }
            method.setAccessible(true);
            method.invoke(handle, pose);
            refreshDimensions(handle);
            return;
        }

        throw new NoSuchMethodException("Could not find setPose(Pose) on " + handle.getClass().getName());
    }

    private ClientBoxState createClientBox(Location location) throws ReflectiveOperationException {
        Object worldHandle = getWorldHandle(location.getWorld());
        Class<?> shulkerClass = nmsClass("net.minecraft.world.entity.monster.Shulker");
        Class<?> entityTypeClass = nmsClass("net.minecraft.world.entity.EntityType");
        Object entityType = entityTypeClass.getField("SHULKER").get(null);

        Constructor<?> constructor = null;
        for (Constructor<?> candidate : shulkerClass.getConstructors()) {
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if (parameterTypes.length == 2 && parameterTypes[0].isAssignableFrom(entityTypeClass)) {
                constructor = candidate;
                break;
            }
        }
        if (constructor == null) {
            throw new NoSuchMethodException("Could not find Shulker(EntityType, Level) constructor");
        }

        Object entity = constructor.newInstance(entityType, worldHandle);
        invokeFirst(entity, location.getX(), location.getY(), location.getZ(), "setPos", "e");
        invokeBoolean(entity, true, "setInvisible");
        invokeBoolean(entity, true, "setNoGravity");
        invokeBoolean(entity, true, "setInvulnerable");
        invokeBoolean(entity, true, "setNoAi", "setNoAI");
        invokeBoolean(entity, true, "setSilent");
        setAttachFaceUp(entity);

        return new ClientBoxState(entity, entityId(entity), new HashSet<>());
    }

    private void updateClientBox(Object entity, Player target) throws ReflectiveOperationException {
        Location playerLocation = target.getLocation().clone();
        Block locationBlock = playerLocation.getBlock();
        int blockSize = (int) ((playerLocation.getY() - playerLocation.getBlockY()) * 100);
        int height = locationBlock.getBoundingBox().getHeight() >= 0.4D || playerLocation.getY() % 0.015625D == 0.0D
                ? (target.getFallDistance() > 0.7F ? 0 : blockSize)
                : 0;

        playerLocation.setY(playerLocation.getY() + (height >= 40 ? 1.5D : 0.5D));

        invokeFirst(entity, height >= 40 ? 100 - height : 0, "setRawPeekAmount", "setPeekAmount");
        invokeFirst(entity, playerLocation.getX(), playerLocation.getY(), playerLocation.getZ(), "setPosRaw", "setPos");
    }

    private Object createEntityMetadataPacket(Object entity) throws ReflectiveOperationException {
        Object dataTracker = getDataTracker(entity);
        if (dataTracker == null) {
            return null;
        }

        Class<?> packetClass = nmsClass(
                "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket",
                "net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata"
        );
        List<?> trackedValues = trackedValues(dataTracker);

        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            Object packet = tryCreatePacket(constructor, entityId(entity), dataTracker, trackedValues);
            if (packet != null) {
                return packet;
            }
        }

        return null;
    }

    private Object createAddEntityPacket(Object entity) throws ReflectiveOperationException {
        Class<?> packetClass = nmsClass(
                "net.minecraft.network.protocol.game.ClientboundAddEntityPacket",
                "net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity"
        );

        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            Object packet = tryCreateAddEntityPacket(constructor, entity);
            if (packet != null) {
                return packet;
            }
        }

        throw new NoSuchMethodException("Could not construct add entity packet");
    }

    private Object createTeleportEntityPacket(Object entity) throws ReflectiveOperationException {
        Class<?> packetClass = nmsClass(
                "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket",
                "net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport"
        );

        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            Object packet = tryCreateTeleportPacket(constructor, entity);
            if (packet != null) {
                return packet;
            }
        }

        throw new NoSuchMethodException("Could not construct teleport entity packet");
    }

    private Object createRemoveEntityPacket(int entityId) throws ReflectiveOperationException {
        Class<?> packetClass = nmsClass(
                "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket",
                "net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy"
        );

        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] arguments = new Object[parameterTypes.length];
            boolean supported = true;

            for (int index = 0; index < parameterTypes.length; index++) {
                Class<?> parameterType = parameterTypes[index];
                if (parameterType == int.class || parameterType == Integer.class) {
                    arguments[index] = entityId;
                } else if (parameterType == int[].class) {
                    arguments[index] = new int[]{entityId};
                } else {
                    supported = false;
                    break;
                }
            }

            if (!supported) {
                continue;
            }

            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        }

        throw new NoSuchMethodException("Could not construct remove entity packet");
    }

    private Object getHandle(Player player) throws ReflectiveOperationException {
        Method method = player.getClass().getMethod("getHandle");
        method.setAccessible(true);
        return method.invoke(player);
    }

    private Object getDataTracker(Object handle) throws ReflectiveOperationException {
        for (String methodName : List.of("getEntityData", "al", "aj", "ai")) {
            try {
                Method method = handle.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(handle);
            } catch (NoSuchMethodException ignored) {
            }
        }

        for (Method method : handle.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String returnName = method.getReturnType().getName();
            if (returnName.contains("SynchedEntityData") || returnName.contains("DataWatcher")) {
                method.setAccessible(true);
                return method.invoke(handle);
            }
        }
        return null;
    }

    private Object getWorldHandle(World world) throws ReflectiveOperationException {
        Method method = world.getClass().getMethod("getHandle");
        method.setAccessible(true);
        return method.invoke(world);
    }

    private List<?> trackedValues(Object dataTracker) throws ReflectiveOperationException {
        for (String methodName : List.of("packDirty", "getNonDefaultValues", "c")) {
            try {
                Method method = dataTracker.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(dataTracker);
                if (value instanceof List<?> list) {
                    return list;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }

        List<?> allValues = allTrackedValues(dataTracker);
        if (allValues != null && !allValues.isEmpty()) {
            return allValues;
        }

        for (Method method : dataTracker.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !List.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            method.setAccessible(true);
            Object value = method.invoke(dataTracker);
            if (value instanceof List<?> list) {
                return list;
            }
        }
        return null;
    }

    private List<?> allTrackedValues(Object dataTracker) throws ReflectiveOperationException {
        for (String methodName : List.of("getAll", "getAllEntries", "packedItems", "b")) {
            try {
                Method method = dataTracker.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(dataTracker);
                if (value instanceof List<?> list) {
                    return list;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }

        for (Field field : dataTracker.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(dataTracker);
            if (value instanceof List<?> list && !list.isEmpty()) {
                return new ArrayList<>(list);
            }
        }

        return null;
    }

    private Object tryCreatePacket(Constructor<?> constructor, int entityId, Object dataTracker, List<?> trackedValues)
            throws ReflectiveOperationException {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];

        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameterType = parameterTypes[index];

            if (parameterType == int.class || parameterType == Integer.class) {
                arguments[index] = entityId;
                continue;
            }

            if (parameterType == boolean.class || parameterType == Boolean.class) {
                arguments[index] = Boolean.TRUE;
                continue;
            }

            if (List.class.isAssignableFrom(parameterType)) {
                if (trackedValues == null || trackedValues.isEmpty()) {
                    return null;
                }
                arguments[index] = trackedValues;
                continue;
            }

            if (parameterType.isInstance(dataTracker)) {
                arguments[index] = dataTracker;
                continue;
            }

            return null;
        }

        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private Object tryCreateAddEntityPacket(Constructor<?> constructor, Object entity) throws ReflectiveOperationException {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        int intIndex = 0;
        int doubleIndex = 0;
        int floatIndex = 0;

        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameterType = parameterTypes[index];

            if (parameterType.isInstance(entity)) {
                arguments[index] = entity;
                continue;
            }

            if (parameterType == int.class || parameterType == Integer.class) {
                arguments[index] = intIndex++ == 0 ? entityId(entity) : 0;
                continue;
            }

            if (parameterType == UUID.class) {
                arguments[index] = entityUuid(entity);
                continue;
            }

            if (parameterType == double.class || parameterType == Double.class) {
                arguments[index] = switch (doubleIndex++) {
                    case 0 -> invokeNoArgs(entity, "getX");
                    case 1 -> invokeNoArgs(entity, "getY");
                    case 2 -> invokeNoArgs(entity, "getZ");
                    default -> invokeNoArgs(entity, "getYHeadRot");
                };
                continue;
            }

            if (parameterType == float.class || parameterType == Float.class) {
                arguments[index] = switch (floatIndex++) {
                    case 0 -> invokeNoArgs(entity, "getXRot");
                    default -> invokeNoArgs(entity, "getYRot");
                };
                continue;
            }

            if (parameterType == byte.class || parameterType == Byte.class) {
                Object rotation = floatIndex++ == 0 ? invokeNoArgs(entity, "getXRot") : invokeNoArgs(entity, "getYRot");
                arguments[index] = (byte) Math.round(((Number) rotation).floatValue());
                continue;
            }

            String typeName = parameterType.getName();
            if (typeName.endsWith("EntityType")) {
                arguments[index] = invokeNoArgs(entity, "getType");
                continue;
            }

            if (typeName.endsWith("Vec3")) {
                arguments[index] = invokeNoArgs(entity, "getDeltaMovement");
                continue;
            }

            return null;
        }

        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private Object tryCreateTeleportPacket(Constructor<?> constructor, Object entity) throws ReflectiveOperationException {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];

        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameterType = parameterTypes[index];

            if (parameterType.isInstance(entity)) {
                arguments[index] = entity;
                continue;
            }

            if (parameterType == int.class || parameterType == Integer.class) {
                arguments[index] = entityId(entity);
                continue;
            }

            if (parameterType == boolean.class || parameterType == Boolean.class) {
                arguments[index] = Boolean.FALSE;
                continue;
            }

            if (Set.class.isAssignableFrom(parameterType)) {
                arguments[index] = Collections.emptySet();
                continue;
            }

            if (parameterType.getName().contains("PositionMoveRotation")) {
                Method ofMethod = parameterType.getMethod("of", nmsClass("net.minecraft.world.entity.Entity"));
                arguments[index] = ofMethod.invoke(null, entity);
                continue;
            }

            return null;
        }

        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private void sendPacket(Player viewer, Object packet) throws ReflectiveOperationException {
        Object handle = getHandle(viewer);
        Object connection = connection(handle);
        if (connection == null) {
            return;
        }

        Method sendMethod = sendMethod(connection.getClass(), packet.getClass());
        if (sendMethod == null) {
            return;
        }
        sendMethod.setAccessible(true);
        sendMethod.invoke(connection, packet);
    }

    private Object connection(Object handle) throws ReflectiveOperationException {
        for (Field field : handle.getClass().getFields()) {
            String typeName = field.getType().getName();
            if (typeName.contains("PlayerConnection") || typeName.contains("ServerGamePacketListenerImpl")) {
                field.setAccessible(true);
                return field.get(handle);
            }
        }

        for (Field field : handle.getClass().getDeclaredFields()) {
            String typeName = field.getType().getName();
            if (typeName.contains("PlayerConnection") || typeName.contains("ServerGamePacketListenerImpl")) {
                field.setAccessible(true);
                return field.get(handle);
            }
        }
        return null;
    }

    private Method sendMethod(Class<?> connectionClass, Class<?> packetClass) {
        Class<?> current = connectionClass;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameter = method.getParameterTypes()[0];
                String name = parameter.getName();
                if ((name.contains(".Packet") || name.endsWith("Packet")) && parameter.isAssignableFrom(packetClass)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Class<?> nmsClass(String... candidates) throws ClassNotFoundException {
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(String.join(", ", candidates));
    }

    private void refreshDimensions(Object handle) throws ReflectiveOperationException {
        for (String methodName : List.of("refreshDimensions", "fz", "fA")) {
            try {
                Method method = handle.getClass().getMethod(methodName);
                method.setAccessible(true);
                method.invoke(handle);
                return;
            } catch (NoSuchMethodException ignored) {
            }
        }
    }

    private Object invokeNoArgs(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private void invokeBoolean(Object target, boolean value, String... methodNames) throws ReflectiveOperationException {
        invokeFirst(target, value, methodNames);
    }

    private void invokeFirst(Object target, Object value, String... methodNames) throws ReflectiveOperationException {
        for (String methodName : methodNames) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                if (!wrap(method.getParameterTypes()[0]).isInstance(value)) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(target, value);
                return;
            }
        }
        throw new NoSuchMethodException(String.join(", ", methodNames));
    }

    private void invokeFirst(Object target, double x, double y, double z, String... methodNames) throws ReflectiveOperationException {
        for (String methodName : methodNames) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 3) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (wrap(parameterTypes[0]) != Double.class || wrap(parameterTypes[1]) != Double.class || wrap(parameterTypes[2]) != Double.class) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(target, x, y, z);
                return;
            }
        }
        throw new NoSuchMethodException(String.join(", ", methodNames));
    }

    private void setAttachFaceUp(Object entity) throws ReflectiveOperationException {
        Class<?> directionClass = nmsClass("net.minecraft.core.Direction");
        Object directionUp = directionClass.getField("UP").get(null);

        for (Method method : entity.getClass().getMethods()) {
            if (!method.getName().equals("setAttachFace") || method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(directionClass)) {
                continue;
            }
            method.setAccessible(true);
            method.invoke(entity, directionUp);
            return;
        }
    }

    private int entityId(Object entity) throws ReflectiveOperationException {
        return ((Number) invokeNoArgs(entity, "getId")).intValue();
    }

    private UUID entityUuid(Object entity) throws ReflectiveOperationException {
        return (UUID) invokeNoArgs(entity, "getUUID");
    }

    private boolean hasSolidBlockAbove(Location location) {
        Location tickLocation = location.clone();
        int blockSize = (int) ((tickLocation.getY() - tickLocation.getBlockY()) * 100);
        tickLocation.setY(tickLocation.getBlockY() + (blockSize >= 40 ? 2.49D : 1.49D));
        Block aboveBlock = tickLocation.getBlock();
        return aboveBlock.getBoundingBox().contains(tickLocation.toVector()) && !aboveBlock.getCollisionShape().getBoundingBoxes().isEmpty();
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private record ClientBoxState(Object entity, int entityId, Set<UUID> spawnedViewers) {
    }
}

