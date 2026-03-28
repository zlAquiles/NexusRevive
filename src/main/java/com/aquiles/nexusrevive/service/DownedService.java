package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.api.event.PlayerDownedEvent;
import com.aquiles.nexusrevive.api.event.PlayerDropDownedEvent;
import com.aquiles.nexusrevive.api.event.PlayerFinalDeathEvent;
import com.aquiles.nexusrevive.api.event.PlayerPickupDownedEvent;
import com.aquiles.nexusrevive.api.event.PlayerReviveEvent;
import com.aquiles.nexusrevive.api.event.PlayerStartReviveEvent;
import com.aquiles.nexusrevive.api.event.PlayerStopReviveEvent;
import com.aquiles.nexusrevive.config.PluginSettings;
import com.aquiles.nexusrevive.model.DownedPlayer;
import com.aquiles.nexusrevive.model.ReviveSession;
import com.aquiles.nexusrevive.model.ReviveZone;
import com.aquiles.nexusrevive.scheduler.NexusTask;
import com.aquiles.nexusrevive.util.PermissionNodes;
import com.aquiles.nexusrevive.util.Components;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DownedService {
    private final NexusRevivePlugin plugin;
    private final Map<UUID, DownedPlayer> downedPlayers = new HashMap<>();
    private final Map<UUID, ReviveSession> reviveByReviver = new HashMap<>();
    private final Map<UUID, NexusTask> suicideTasks = new HashMap<>();
    private final Map<UUID, NexusTask> downedTickTasks = new HashMap<>();
    private final Map<UUID, Location> fakeBarrierLocations = new HashMap<>();
    private final Set<UUID> pendingFinalDeaths = new HashSet<>();
    private final Set<UUID> forcedDismounts = new HashSet<>();
    private final Map<UUID, Map<String, Integer>> soundCooldowns = new HashMap<>();
    private final Map<UUID, Set<PotionEffectType>> managedDownedEffects = new HashMap<>();
    private final File stateFile;
    private NexusTask persistenceTask;
    private boolean stateDirty;

    public DownedService(NexusRevivePlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "states.yml");
        if (plugin.getPluginSettings().persistence().enabled()) {
            loadPersistentState();
        }
        restartDownedTicksForOnlinePlayers();
        startPersistence();
    }

    public void reload() {
        if (persistenceTask != null) {
            persistenceTask.cancel();
        }
        for (NexusTask task : downedTickTasks.values()) {
            task.cancel();
        }
        downedTickTasks.clear();
        restartDownedTicksForOnlinePlayers();
        startPersistence();
        for (UUID playerId : downedPlayers.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            applyConfiguredDownedEffects(player);
        }
    }

    public void shutdown() {
        if (plugin.getPluginSettings().persistence().enabled()) {
            prepareStatesForShutdown();
            savePersistentState();
        }
        if (persistenceTask != null) {
            persistenceTask.cancel();
        }
        downedTickTasks.values().forEach(NexusTask::cancel);
        downedTickTasks.clear();
        reviveByReviver.values().forEach(session -> {
            if (session.getTask() != null) {
                session.getTask().cancel();
            }
        });
        suicideTasks.values().forEach(NexusTask::cancel);
        reviveByReviver.clear();
        suicideTasks.clear();
        for (UUID uuid : downedPlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restoreState(player);
            }
        }
        fakeBarrierLocations.clear();
        forcedDismounts.clear();
        soundCooldowns.clear();
        managedDownedEffects.clear();
        downedPlayers.clear();
    }

    public boolean tryDown(Player victim, Player attacker, EntityDamageEvent.DamageCause cause) {
        return tryDown(victim, attacker, cause, 0.0D);
    }

    public boolean tryDown(Player victim, Player attacker, EntityDamageEvent.DamageCause cause, double incomingDamage) {
        PluginSettings settings = plugin.getPluginSettings();
        if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (isDowned(victim)) {
            return false;
        }
        if (!settings.restrictions().worldMode().allows(victim.getWorld().getName(), settings.restrictions().worlds())) {
            return false;
        }
        if (settings.restrictions().ignoredDamageCauses().contains(cause)) {
            return false;
        }
        if (!victim.hasPermission(PermissionNodes.DOWNABLE)) {
            return false;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            double maxDistance = settings.downedInteractions().maxFallDistance();
            if (maxDistance >= 0 && victim.getFallDistance() > maxDistance) {
                return false;
            }
        }
        if (!plugin.getCompatibilityService().canDown(victim, attacker)) {
            return false;
        }
        PlayerDownedEvent downedEvent = new PlayerDownedEvent(victim, attacker, cause);
        Bukkit.getPluginManager().callEvent(downedEvent);
        if (downedEvent.isCancelled()) {
            return false;
        }

        DownedPlayer downed = new DownedPlayer(
                victim.getUniqueId(),
                attacker == null ? null : attacker.getUniqueId(),
                settings.mechanics().deathDelaySeconds(),
                settings.mechanics().invulnerabilitySeconds()
        );
        plugin.getLootService().closeForPlayer(victim);
        downedPlayers.put(victim.getUniqueId(), downed);

        victim.leaveVehicle();
        victim.setFallDistance(0.0F);
        victim.setSprinting(false);
        victim.setWalkSpeed((float) settings.mechanics().downedWalkSpeed());
        victim.setHealth(Math.max(0.5D, Math.min(maxHealth(victim), settings.mechanics().downedHealth())));
        playDownedEntryAnimation(victim, attacker, incomingDamage);
        plugin.getSchedulerFacade().runEntityLater(victim, () -> {
            if (victim.isOnline() && !victim.isDead() && isDowned(victim)) {
                applyDownedPose(victim);
            }
        }, () -> removeDownedState(victim, false), Math.max(1L, settings.mechanics().downedEntryDelayTicks()));
        applyConfiguredDownedEffects(victim);
        startDownedTick(victim);

        Map<String, String> placeholders = placeholders(victim, attacker, null);
        enrichDownedPlaceholders(placeholders, downed);
        plugin.getMessages().send(victim, "victim.downed", placeholders);
        if (attacker != null) {
            plugin.getMessages().send(attacker, "attacker.downed", placeholders);
        }
        runConfiguredEvent("events.player-downed", eventPlaceholders(victim, attacker, null, null), eventSenders(victim, attacker, null, null));
        markStateDirty();
        return true;
    }

    public boolean isDowned(Player player) {
        return downedPlayers.containsKey(player.getUniqueId());
    }

    public boolean canTakeDamage(Player player) {
        DownedPlayer downed = downedPlayers.get(player.getUniqueId());
        return downed == null || downed.getInvulnerabilitySeconds() <= 0;
    }

    public Map<UUID, DownedPlayer> getDownedPlayers() {
        return downedPlayers;
    }

    public Optional<DownedPlayer> getDownedState(Player player) {
        return Optional.ofNullable(downedPlayers.get(player.getUniqueId()));
    }

    public boolean isBeingRevived(Player player) {
        DownedPlayer downed = downedPlayers.get(player.getUniqueId());
        return downed != null && downed.getActiveReviverId() != null;
    }

    public boolean isAutoReviving(Player player) {
        DownedPlayer downed = downedPlayers.get(player.getUniqueId());
        return downed != null && downed.isAutoReviving();
    }

    public boolean isForcedDismount(Player player) {
        return forcedDismounts.contains(player.getUniqueId());
    }

    public Optional<ReviveSession> getReviveSession(Player reviver) {
        return Optional.ofNullable(reviveByReviver.get(reviver.getUniqueId()));
    }

    public Optional<Player> getReviveVictim(Player reviver) {
        ReviveSession session = reviveByReviver.get(reviver.getUniqueId());
        if (session == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Bukkit.getPlayer(session.getVictimId()));
    }

    public Optional<Player> getCarriedVictim(Player picker) {
        return picker.getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .filter(this::isDowned)
                .findFirst();
    }

    public Map<String, String> createVictimHudPlaceholders(Player player) {
        DownedPlayer downed = downedPlayers.get(player.getUniqueId());
        if (downed == null) {
            return Map.of();
        }

        Player attacker = downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId());
        Player reviver = downed.getActiveReviverId() == null ? null : Bukkit.getPlayer(downed.getActiveReviverId());
        Player picker = downed.getCarrierId() == null ? null : Bukkit.getPlayer(downed.getCarrierId());

        Map<String, String> placeholders = placeholders(player, attacker, reviver);
        placeholders.put("reviver", reviver == null ? "Nadie" : reviver.getName());
        placeholders.put("picker", picker == null ? "Nadie" : picker.getName());
        enrichDownedPlaceholders(placeholders, downed);

        ReviveSession session = reviver == null ? null : reviveByReviver.get(reviver.getUniqueId());
        if (session != null) {
            enrichRevivePlaceholders(placeholders, session);
        } else if (downed.isAutoReviving()) {
            enrichAutoRevivePlaceholders(placeholders, downed, requiredAutoReviveSeconds(player.getLocation()));
            placeholders.put("reviver", "Zona " + downed.getAutoReviveZoneName());
            placeholders.put("auto_reviving", "true");
        } else {
            placeholders.put("progress", "0");
            placeholders.put("progress_bar", buildBar("progress", 0.0D, 1.0D, 12, "&a", "&8"));
        }
        return placeholders;
    }

    public Map<String, String> createReviverHudPlaceholders(Player reviver) {
        ReviveSession session = reviveByReviver.get(reviver.getUniqueId());
        if (session == null) {
            return Map.of();
        }

        Player victim = Bukkit.getPlayer(session.getVictimId());
        if (victim == null) {
            return Map.of();
        }

        DownedPlayer downed = downedPlayers.get(victim.getUniqueId());
        if (downed == null) {
            return Map.of();
        }

        Player attacker = downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId());
        Map<String, String> placeholders = placeholders(victim, attacker, reviver);
        placeholders.put("distance", formatDistance(reviver.getLocation(), victim.getLocation()));
        enrichDownedPlaceholders(placeholders, downed);
        enrichRevivePlaceholders(placeholders, session);
        return placeholders;
    }

    public Map<String, String> createPickerHudPlaceholders(Player picker) {
        Optional<Player> victimOptional = getCarriedVictim(picker);
        if (victimOptional.isEmpty()) {
            return Map.of();
        }

        Player victim = victimOptional.get();
        DownedPlayer downed = downedPlayers.get(victim.getUniqueId());
        if (downed == null) {
            return Map.of();
        }

        Player attacker = downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId());
        Map<String, String> placeholders = placeholders(victim, attacker, picker);
        placeholders.put("picker", picker.getName());
        enrichDownedPlaceholders(placeholders, downed);
        return placeholders;
    }

    public boolean revive(Player victim, Player reviver) {
        DownedPlayer downed = downedPlayers.get(victim.getUniqueId());
        if (downed == null) {
            return false;
        }

        boolean autoRevive = downed.isAutoReviving();
        String autoReviveZone = downed.getAutoReviveZoneName();

        removeDownedState(victim, false);
        victim.setHealth(Math.max(1.0D, Math.min(maxHealth(victim), plugin.getPluginSettings().mechanics().revivedHealth())));
        if (autoRevive) {
            playAutoReviveFinishEffect(victim);
        }
        Bukkit.getPluginManager().callEvent(new PlayerReviveEvent(victim, reviver));

        Map<String, String> placeholders = placeholders(victim, downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId()), reviver);
        placeholders.put("auto_revive_zone", autoReviveZone == null || autoReviveZone.isBlank() ? "none" : autoReviveZone);
        placeholders.put("in_auto_revive_zone", Boolean.toString(autoRevive));
        placeholders.put("auto_reviving", Boolean.toString(autoRevive));
        plugin.getMessages().send(victim, "victim.revived", placeholders);
        if (reviver != null) {
            plugin.getMessages().send(reviver, "reviver.success", placeholders);
        }
        playConfiguredSound(reviver, victim, plugin.getPluginSettings().sounds().successRelive());
        runConfiguredEvent(
                "events.player-revived",
                eventPlaceholders(victim, downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId()), reviver, null),
                eventSenders(victim, downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId()), reviver, null)
        );
        markStateDirty();
        return true;
    }

    public boolean killDowned(Player victim) {
        DownedPlayer downed = downedPlayers.get(victim.getUniqueId());
        if (downed == null) {
            return false;
        }

        Player attacker = downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId());
        Bukkit.getPluginManager().callEvent(new PlayerFinalDeathEvent(victim, attacker));
        removeDownedState(victim, false);
        runConfiguredEvent("events.player-final-death", eventPlaceholders(victim, attacker, null, null), eventSenders(victim, attacker, null, null));
        pendingFinalDeaths.add(victim.getUniqueId());
        plugin.getSchedulerFacade().runEntityNow(victim, () -> victim.setHealth(0.0D), () -> {
        });
        markStateDirty();
        return true;
    }

    public boolean isPendingFinalDeath(Player player) {
        return pendingFinalDeaths.contains(player.getUniqueId());
    }

    public void clearPendingFinalDeath(Player player) {
        pendingFinalDeaths.remove(player.getUniqueId());
        markStateDirty();
    }

    public void cleanupAfterDeath(Player player, boolean clearPendingFinalDeath) {
        removeDownedState(player, clearPendingFinalDeath);
    }

    public void cleanupAfterRespawn(Player player) {
        removeDownedState(player, true);
        player.sendActionBar(Component.empty());
    }

    public boolean startRevive(Player reviver, Player victim) {
        PluginSettings settings = plugin.getPluginSettings();
        if (!isDowned(victim) || (reviver.equals(victim) && !settings.revive().allowSelfRevive())) {
            return false;
        }
        if (!reviver.hasPermission(PermissionNodes.REVIVER)) {
            return false;
        }
        if (!victim.hasPermission(PermissionNodes.REVIVABLE)) {
            return false;
        }
        if (reviveByReviver.containsKey(reviver.getUniqueId()) || !canReviveInCurrentLocation(victim.getLocation())) {
            return false;
        }
        if (!reviver.getWorld().equals(victim.getWorld()) || reviver.getLocation().distance(victim.getLocation()) > settings.revive().startDistance()) {
            return false;
        }

        DownedPlayer downed = downedPlayers.get(victim.getUniqueId());
        if (downed == null || downed.getActiveReviverId() != null) {
            return false;
        }
        if (downed.getCarrierId() != null || victim.getVehicle() != null) {
            plugin.getMessages().send(reviver, "reviver.carry-blocked", placeholders(victim, null, reviver));
            return false;
        }
        if (!hasRequiredReviveItem(reviver)) {
            plugin.getMessages().send(reviver, "reviver.item-required");
            return false;
        }
        CompatibilityService.BlockReason reviveReason = plugin.getCompatibilityService().canStartRevive(reviver, victim);
        if (reviveReason != CompatibilityService.BlockReason.NONE) {
            sendBlockReason(reviver, reviveReason);
            return false;
        }
        PlayerStartReviveEvent startReviveEvent = new PlayerStartReviveEvent(victim, reviver);
        Bukkit.getPluginManager().callEvent(startReviveEvent);
        if (startReviveEvent.isCancelled()) {
            return false;
        }

        double duration = settings.revive().durationSeconds();
        if (settings.revive().zonesAffectSpeed()) {
            duration = duration / Math.max(0.1D, plugin.getZoneService().getSpeedMultiplier(victim.getLocation()));
        }

        ReviveSession session = new ReviveSession(reviver.getUniqueId(), victim.getUniqueId(), duration);
        NexusTask task = plugin.getSchedulerFacade().runEntityTimer(
                reviver,
                () -> tickRevive(session),
                () -> stopBySession(session),
                1L,
                5L
        );
        session.setTask(task);
        downed.setActiveReviverId(reviver.getUniqueId());
        reviveByReviver.put(reviver.getUniqueId(), session);
        if (!plugin.getPluginSettings().loot().allowWhileReviving()) {
            plugin.getLootService().closeByVictim(victim, true, "loot.target-closed");
        }

        Player attacker = downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId());
        Map<String, String> placeholders = placeholders(victim, attacker, reviver);
        plugin.getMessages().send(reviver, "reviver.start", placeholders);
        plugin.getMessages().send(victim, "victim.start-revive", placeholders);
        playConfiguredSound(reviver, victim, plugin.getPluginSettings().sounds().startReliving());
        runConfiguredEvent("events.player-start-revive", eventPlaceholders(victim, attacker, reviver, null), eventSenders(victim, attacker, reviver, null));
        return true;
    }

    public void stopRevive(Player reviver, boolean notify) {
        ReviveSession session = reviveByReviver.remove(reviver.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.getTask() != null) {
            session.getTask().cancel();
        }

        Player victim = Bukkit.getPlayer(session.getVictimId());
        if (victim != null) {
            DownedPlayer downed = downedPlayers.get(victim.getUniqueId());
            Player attacker = downed != null && downed.getAttackerId() != null ? Bukkit.getPlayer(downed.getAttackerId()) : null;
            if (downed != null) {
                downed.setActiveReviverId(null);
            }
            if (notify) {
                Bukkit.getPluginManager().callEvent(new PlayerStopReviveEvent(victim, reviver));
                Map<String, String> placeholders = placeholders(victim, attacker, reviver);
                plugin.getMessages().send(reviver, "reviver.stop", placeholders);
                plugin.getMessages().send(victim, "victim.stop-revive", placeholders);
                playConfiguredSound(reviver, victim, plugin.getPluginSettings().sounds().stopReliving());
                runConfiguredEvent("events.player-stop-revive", eventPlaceholders(victim, attacker, reviver, null), eventSenders(victim, attacker, reviver, null));
            }
        }
    }

    public Optional<Player> findNearestDowned(Player player) {
        return downedPlayers.keySet().stream()
                .map(uuid -> Bukkit.getPlayer(uuid))
                .filter(target -> target != null && target.isOnline() && !target.equals(player))
                .filter(target -> plugin.getCompatibilityService().canSeeDowned(player, target))
                .filter(target -> target.getWorld().equals(player.getWorld()))
                .filter(target -> target.getLocation().distance(player.getLocation()) <= plugin.getPluginSettings().revive().startDistance())
                .min(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(player.getLocation())));
    }

    public boolean pickUp(Player picker, Player victim) {
        PluginSettings settings = plugin.getPluginSettings();
        if (!settings.carry().enabled() || !isDowned(victim)) {
            return false;
        }
        if (!picker.hasPermission(PermissionNodes.PICKER)) {
            return false;
        }
        if (!victim.hasPermission(PermissionNodes.PICKABLE)) {
            return false;
        }
        if (!picker.getPassengers().isEmpty() || victim.getVehicle() != null) {
            return false;
        }

        DownedPlayer downed = downedPlayers.get(victim.getUniqueId());
        if (downed == null) {
            return false;
        }
        CompatibilityService.BlockReason carryReason = plugin.getCompatibilityService().canCarry(picker, victim);
        if (carryReason != CompatibilityService.BlockReason.NONE) {
            sendBlockReason(picker, carryReason);
            return false;
        }
        PlayerPickupDownedEvent pickupEvent = new PlayerPickupDownedEvent(victim, picker);
        Bukkit.getPluginManager().callEvent(pickupEvent);
        if (pickupEvent.isCancelled()) {
            return false;
        }
        if (!plugin.getPluginSettings().loot().allowWhileCarried()) {
            plugin.getLootService().closeByVictim(victim, true, "loot.target-closed");
        }

        picker.addPassenger(victim);
        downed.setCarrierId(picker.getUniqueId());
        Player attacker = downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId());
        plugin.getMessages().send(picker, "picker.pickup", placeholders(victim, attacker, picker));
        plugin.getMessages().send(victim, "victim.picked-up", placeholders(victim, attacker, picker));
        runConfiguredEvent("events.player-picked-up", eventPlaceholders(victim, attacker, null, picker), eventSenders(victim, attacker, null, picker));
        return true;
    }

    public boolean isCarryingDowned(Player picker) {
        for (var passenger : picker.getPassengers()) {
            if (passenger instanceof Player victim && isDowned(victim)) {
                return true;
            }
        }
        return false;
    }

    public void dropCarried(Player picker) {
        for (var passenger : picker.getPassengers()) {
            if (!(passenger instanceof Player victim) || !isDowned(victim)) {
                continue;
            }
            markForcedDismount(victim);
            picker.removePassenger(victim);
            DownedPlayer downed = downedPlayers.get(victim.getUniqueId());
            Player attacker = downed != null && downed.getAttackerId() != null ? Bukkit.getPlayer(downed.getAttackerId()) : null;
            if (downed != null) {
                downed.setCarrierId(null);
            }
            Bukkit.getPluginManager().callEvent(new PlayerDropDownedEvent(victim, picker));
            plugin.getMessages().send(picker, "picker.drop", placeholders(victim, attacker, picker));
            plugin.getMessages().send(victim, "victim.dropped", placeholders(victim, attacker, picker));
            runConfiguredEvent("events.player-dropped", eventPlaceholders(victim, attacker, null, picker), eventSenders(victim, attacker, null, picker));
        }
    }

    public void startSuicide(Player player) {
        if (!plugin.getConfig().getBoolean("suicide.enabled", true) || !isDowned(player) || suicideTasks.containsKey(player.getUniqueId())) {
            return;
        }

        int countdown = plugin.getConfig().getInt("suicide.hold-seconds", 5);
        NexusTask task = plugin.getSchedulerFacade().runEntityTimer(player, new Runnable() {
            private int secondsLeft = countdown;

            @Override
            public void run() {
                if (!player.isOnline() || !player.isSneaking() || !isDowned(player)) {
                    cancelSuicide(player);
                    return;
                }
                if (secondsLeft <= 0) {
                    killDowned(player);
                    cancelSuicide(player);
                    return;
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("suicide_seconds", Integer.toString(secondsLeft));
                placeholders.put("suicide_bar", buildBar(
                        "suicide",
                        countdown - secondsLeft,
                        Math.max(1, countdown),
                        12,
                        "&c",
                        "&8"
                ));
                player.sendActionBar(plugin.getMessages().component("actionbar.suicide", placeholders));
                secondsLeft--;
            }
        }, () -> cancelSuicide(player), 1L, 20L);
        suicideTasks.put(player.getUniqueId(), task);
    }

    public void cancelSuicide(Player player) {
        NexusTask task = suicideTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void handleDisconnect(Player player) {
        if (!isDowned(player)) {
            stopRevive(player, false);
            plugin.getLootService().closeForPlayer(player);
            if (isCarryingDowned(player)) {
                dropCarried(player);
            }
            return;
        }
        if (plugin.isServerStopping()) {
            prepareTransientStateForDisconnect(player, true);
            return;
        }
        if (plugin.getPluginSettings().mechanics().killOnDisconnect()) {
            DownedPlayer downed = downedPlayers.get(player.getUniqueId());
            Player attacker = downed != null && downed.getAttackerId() != null
                    ? Bukkit.getPlayer(downed.getAttackerId())
                    : null;
            runConfiguredEvent(
                    "events.player-final-death",
                    eventPlaceholders(player, attacker, null, null),
                    eventSenders(player, attacker, null, null)
            );
            pendingFinalDeaths.add(player.getUniqueId());
            removeDownedState(player, false);
            if (!player.isDead()) {
                player.setHealth(0.0D);
            }
            markStateDirty();
            return;
        }
        prepareTransientStateForDisconnect(player, false);
    }

    public void handleReconnect(Player player) {
        if (!isPendingFinalDeath(player) || player.isDead()) {
            DownedPlayer downed = downedPlayers.get(player.getUniqueId());
            if (downed == null || player.isDead()) {
                return;
            }
            plugin.getSchedulerFacade().runEntityLater(player, () -> {
                if (!player.isOnline() || player.isDead() || !isDowned(player)) {
                    return;
                }
                reapplyDownedState(player);
                startDownedTick(player);
                plugin.getMessages().send(
                        player,
                        "victim.reconnected-downed",
                        createVictimHudPlaceholders(player)
                );
            }, () -> {
            }, 1L);
            return;
        }
        plugin.getSchedulerFacade().runEntityLater(player, () -> {
            if (!player.isOnline() || player.isDead() || !isPendingFinalDeath(player)) {
                return;
            }
            player.setHealth(0.0D);
        }, () -> {
        }, 1L);
    }

    public boolean canReviveInCurrentLocation(Location location) {
        return plugin.getPluginSettings().revive().zoneMode() == PluginSettings.ZoneMode.ANYWHERE
                || plugin.getZoneService().getZoneAt(location).isPresent();
    }

    public void refreshPose(Player player) {
        if (!isDowned(player) || !player.isOnline() || player.isDead()) {
            return;
        }
        syncFakeBarrier(player);
        plugin.getDownedPoseAdapter().refreshDownedPose(player);
    }

    private boolean tickAutoRevive(Player player, DownedPlayer downed) {
        if (downed.getCarrierId() != null) {
            downed.clearAutoRevive();
            return false;
        }

        Optional<ReviveZone> zoneOptional = plugin.getZoneService().getAutoReviveZone(player.getLocation());
        if (zoneOptional.isEmpty()) {
            downed.clearAutoRevive();
            return false;
        }

        ReviveZone zone = zoneOptional.get();
        if (!zone.name().equalsIgnoreCase(downed.getAutoReviveZoneName())) {
            downed.setAutoReviveProgressSeconds(0.0D);
        }
        downed.setAutoReviveZoneName(zone.name());
        downed.addAutoReviveProgressSeconds(1.0D);

        if (downed.getAutoReviveProgressSeconds() >= requiredAutoReviveSeconds(player.getLocation())) {
            revive(player, null);
            return false;
        }
        return true;
    }

    private double requiredAutoReviveSeconds(Location location) {
        double required = plugin.getPluginSettings().revive().durationSeconds();
        if (plugin.getPluginSettings().revive().zonesAffectSpeed()) {
            required = required / Math.max(0.1D, plugin.getZoneService().getSpeedMultiplier(location));
        }
        return Math.max(0.1D, required);
    }

    private void tickRevive(ReviveSession session) {
        Player reviver = Bukkit.getPlayer(session.getReviverId());
        Player victim = Bukkit.getPlayer(session.getVictimId());
        if (reviver == null || victim == null || !reviver.isOnline() || !victim.isOnline()) {
            stopBySession(session);
            return;
        }
        if (reviver.isDead() || victim.isDead() || !isDowned(victim) || !reviver.isSneaking()) {
            stopRevive(reviver, true);
            return;
        }
        if (!reviver.getWorld().equals(victim.getWorld())
                || reviver.getLocation().distance(victim.getLocation()) > plugin.getPluginSettings().revive().cancelDistance()
                || !canReviveInCurrentLocation(victim.getLocation())) {
            stopRevive(reviver, true);
            return;
        }

        session.addProgress(0.25D);
        Map<String, String> placeholders = placeholders(victim, null, reviver);
        enrichRevivePlaceholders(placeholders, session);
        reviver.sendActionBar(plugin.getMessages().component("actionbar.reviver", placeholders));
        victim.sendActionBar(plugin.getMessages().component("actionbar.victim-reviving", placeholders));
        playConfiguredSound(reviver, victim, plugin.getPluginSettings().sounds().reliving());

        if (session.getProgressSeconds() >= session.getRequiredSeconds()) {
            stopBySession(session);
            if (!plugin.getCompatibilityService().chargeRevive(reviver)) {
                sendBlockReason(reviver, CompatibilityService.BlockReason.NO_MONEY);
                return;
            }
            if (!consumeRequiredReviveItem(reviver)) {
                plugin.getMessages().send(reviver, "reviver.item-required");
                return;
            }
            revive(victim, reviver);
        }
    }

    private void stopBySession(ReviveSession session) {
        Player reviver = Bukkit.getPlayer(session.getReviverId());
        if (reviver != null) {
            stopRevive(reviver, false);
        } else if (session.getTask() != null) {
            session.getTask().cancel();
        }
    }

    private void cancelReviveForVictim(UUID victimId) {
        reviveByReviver.values().stream()
                .filter(session -> session.getVictimId().equals(victimId))
                .findFirst()
                .ifPresent(session -> {
                    Player reviver = Bukkit.getPlayer(session.getReviverId());
                    if (reviver != null) {
                        stopRevive(reviver, false);
                    } else if (session.getTask() != null) {
                        session.getTask().cancel();
                    }
                });
    }

    private void startPersistence() {
        if (!plugin.getPluginSettings().persistence().enabled()) {
            return;
        }
        persistenceTask = plugin.getSchedulerFacade().runGlobalTimer(this::flushDirtyState, 100L, 100L);
    }

    private void restartDownedTicksForOnlinePlayers() {
        for (UUID playerId : downedPlayers.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && !player.isDead()) {
                startDownedTick(player);
            }
        }
    }

    private void startDownedTick(Player player) {
        stopDownedTick(player.getUniqueId());
        if (!isDowned(player)) {
            return;
        }

        downedTickTasks.put(
                player.getUniqueId(),
                plugin.getSchedulerFacade().runEntityTimer(
                        player,
                        () -> tickDownedPlayer(player),
                        () -> stopDownedTick(player.getUniqueId()),
                        20L,
                        20L
                )
        );
    }

    private void stopDownedTick(UUID playerId) {
        NexusTask task = downedTickTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void tickDownedPlayer(Player player) {
        DownedPlayer downed = downedPlayers.get(player.getUniqueId());
        if (downed == null || !player.isOnline() || player.isDead()) {
            stopDownedTick(player.getUniqueId());
            return;
        }

        downed.tickInvulnerability();
        boolean isBeingRevived = downed.getActiveReviverId() != null;
        boolean pausesWhileCarried = plugin.getPluginSettings().carry().stopDeathTimerWhileCarried() && downed.getCarrierId() != null;
        boolean autoReviving = false;
        if (!isBeingRevived) {
            autoReviving = tickAutoRevive(player, downed);
        }
        if (!isDowned(player)) {
            markStateDirty();
            return;
        }
        if (!isBeingRevived && !autoReviving && !pausesWhileCarried) {
            downed.tickDeathTimer();
        }

        if (!isBeingRevived) {
            Map<String, String> placeholders = placeholders(
                    player,
                    downed.getAttackerId() == null ? null : Bukkit.getPlayer(downed.getAttackerId()),
                    null
            );
            enrichDownedPlaceholders(placeholders, downed);
            if (autoReviving) {
                placeholders.put("reviver", "Zona " + downed.getAutoReviveZoneName());
                enrichAutoRevivePlaceholders(placeholders, downed, requiredAutoReviveSeconds(player.getLocation()));
                player.sendActionBar(plugin.getMessages().component("actionbar.victim-reviving", placeholders));
            } else {
                player.sendActionBar(plugin.getMessages().component("actionbar.victim-waiting", placeholders));
            }
        } else {
            downed.clearAutoRevive();
        }

        if (downed.getSecondsUntilDeath() <= 0) {
            if (plugin.getPluginSettings().mechanics().deathAction() == PluginSettings.DeathAction.REVIVE) {
                revive(player, null);
            } else {
                killDowned(player);
            }
        }
        markStateDirty();
    }

    private void applyConfiguredDownedEffects(Player player) {
        clearManagedDownedEffects(player);

        PluginSettings.DownedEffects settings = plugin.getPluginSettings().downedEffects();
        if (!settings.enabled() || settings.effects().isEmpty()) {
            return;
        }

        Set<PotionEffectType> applied = new HashSet<>();
        for (PluginSettings.DownedEffects.EffectRule rule : settings.effects().values()) {
            PotionEffectType type = rule.type();
            if (type == null) {
                continue;
            }

            player.addPotionEffect(new PotionEffect(
                    type,
                    Integer.MAX_VALUE,
                    rule.amplifier(),
                    rule.ambient(),
                    rule.particles(),
                    rule.icon()
            ));
            applied.add(type);
        }

        if (!applied.isEmpty()) {
            managedDownedEffects.put(player.getUniqueId(), applied);
        }
    }

    private void clearManagedDownedEffects(Player player) {
        Set<PotionEffectType> effectTypes = new HashSet<>();
        Set<PotionEffectType> tracked = managedDownedEffects.remove(player.getUniqueId());
        if (tracked != null) {
            effectTypes.addAll(tracked);
        }

        PluginSettings.DownedEffects settings = plugin.getPluginSettings().downedEffects();
        effectTypes.addAll(settings.effects().values().stream()
                .map(PluginSettings.DownedEffects.EffectRule::type)
                .filter(type -> type != null)
                .toList());

        effectTypes.add(PotionEffectType.SLOWNESS);
        effectTypes.add(PotionEffectType.WEAKNESS);

        for (PotionEffectType type : effectTypes) {
            player.removePotionEffect(type);
        }
    }

    private void restoreState(Player player) {
        player.setWalkSpeed(0.2F);
        clearManagedDownedEffects(player);
        clearFakeBarrier(player);
        plugin.getDownedPoseAdapter().clearDownedPose(player);
    }

    private void removeDownedState(Player player, boolean clearPendingFinalDeath) {
        cancelReviveForVictim(player.getUniqueId());
        cancelSuicide(player);
        plugin.getLootService().closeByVictim(player, true, "loot.target-closed");
        plugin.getLootService().closeForPlayer(player);
        dropCarried(player);
        markForcedDismount(player);
        player.leaveVehicle();
        downedPlayers.remove(player.getUniqueId());
        restoreState(player);
        player.sendActionBar(Component.empty());
        if (clearPendingFinalDeath) {
            pendingFinalDeaths.remove(player.getUniqueId());
        }
        markStateDirty();
    }

    private void applyDownedPose(Player player) {
        syncFakeBarrier(player);
        plugin.getDownedPoseAdapter().applyDownedPose(player);
    }

    private void sendBlockReason(Player player, CompatibilityService.BlockReason reason) {
        switch (reason) {
            case IN_COMBAT -> plugin.getMessages().send(player, "hooks.in-combat");
            case REGION_BLOCKED -> plugin.getMessages().send(player, "hooks.region-blocked");
            case NO_MONEY -> plugin.getMessages().send(
                    player,
                    "hooks.not-enough-money",
                    Map.of("amount", plugin.getCompatibilityService().reviveCostText())
            );
            case TARGET_HIDDEN -> plugin.getMessages().send(player, "hooks.target-hidden");
            default -> {
            }
        }
    }

    private boolean hasRequiredReviveItem(Player player) {
        PluginSettings.ReviveItemRequirement requirement = plugin.getPluginSettings().revive().requiredItem();
        return !requirement.enabled() || findMatchingReviveItem(player) != null;
    }

    private boolean consumeRequiredReviveItem(Player player) {
        PluginSettings.ReviveItemRequirement requirement = plugin.getPluginSettings().revive().requiredItem();
        if (!requirement.enabled()) {
            return true;
        }

        ItemStack matchingItem = findMatchingReviveItem(player);
        if (matchingItem == null) {
            return false;
        }

        if (requirement.consumeOnSuccess()) {
            matchingItem.setAmount(matchingItem.getAmount() - 1);
        }
        return true;
    }

    private ItemStack findMatchingReviveItem(Player player) {
        PluginSettings.ReviveItemRequirement requirement = plugin.getPluginSettings().revive().requiredItem();
        if (!requirement.enabled() || requirement.items().isEmpty()) {
            return null;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (matchesAnyReviveItem(mainHand, requirement)) {
            return mainHand;
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (matchesAnyReviveItem(offHand, requirement)) {
            return offHand;
        }
        return null;
    }

    private boolean matchesAnyReviveItem(ItemStack stack, PluginSettings.ReviveItemRequirement requirement) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }

        for (PluginSettings.ReviveItemRequirement.ItemRule rule : requirement.items().values()) {
            if (matchesReviveItemRule(stack, rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesReviveItemRule(ItemStack stack, PluginSettings.ReviveItemRequirement.ItemRule rule) {
        if (rule.material() != null && stack.getType() != rule.material()) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        if (rule.hasCustomModelData()) {
            if (meta == null || !meta.hasCustomModelDataComponent()) {
                return false;
            }
            var modelData = meta.getCustomModelDataComponent().getFloats();
            int value = modelData.isEmpty() ? Integer.MIN_VALUE : Math.round(modelData.getFirst());
            if (value != rule.customModelData()) {
                return false;
            }
        }

        if (rule.nameContains() != null && !rule.nameContains().isBlank()) {
            if (meta == null || !meta.hasDisplayName()) {
                return false;
            }
            String expected = Components.legacy(Components.colorize(rule.nameContains()));
            String actual = Components.legacy(meta.displayName());
            if (!actual.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    private void playConfiguredSound(Player first, Player second, PluginSettings.Sounds.ConfiguredSound configuredSound) {
        if (configuredSound == null || !configuredSound.enabled()) {
            return;
        }

        Sound sound = resolveSound(configuredSound.sound());
        if (sound == null) {
            return;
        }

        playSound(first, sound, configuredSound);
        if (second != null && !second.equals(first)) {
            playSound(second, sound, configuredSound);
        }
    }

    private void playSound(Player player, Sound sound, PluginSettings.Sounds.ConfiguredSound configuredSound) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!canPlaySound(player, sound, configuredSound.cooldownTicks())) {
            return;
        }
        player.playSound(
                player.getLocation(),
                sound,
                SoundCategory.PLAYERS,
                configuredSound.volume(),
                configuredSound.pitch()
        );
    }

    private Sound resolveSound(String rawSound) {
        if (rawSound == null || rawSound.isBlank()) {
            return null;
        }

        for (Field field : Sound.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Sound.class.isAssignableFrom(field.getType())) {
                continue;
            }
            if (!field.getName().equalsIgnoreCase(rawSound)) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (value instanceof Sound sound) {
                    return sound;
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        Object[] constants = Sound.class.getEnumConstants();
        if (constants == null) {
            return null;
        }

        for (Object constant : constants) {
            if (constant instanceof Sound sound
                    && constant instanceof Enum<?> enumConstant
                    && enumConstant.name().equalsIgnoreCase(rawSound)) {
                return sound;
            }
        }
        return null;
    }

    private boolean canPlaySound(Player player, Sound sound, long cooldownTicks) {
        if (cooldownTicks <= 0L) {
            return true;
        }

        int currentTick = Bukkit.getCurrentTick();
        Map<String, Integer> playerCooldownMap = soundCooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        String key = soundKey(sound);
        Integer lastTick = playerCooldownMap.get(key);
        if (lastTick != null && currentTick - lastTick < cooldownTicks) {
            return false;
        }

        playerCooldownMap.put(key, currentTick);
        return true;
    }

    private String soundKey(Sound sound) {
        if (sound instanceof Enum<?> enumSound) {
            return enumSound.name();
        }
        return sound.toString();
    }

    private void enrichDownedPlaceholders(Map<String, String> placeholders, DownedPlayer downed) {
        PluginSettings.Mechanics mechanics = plugin.getPluginSettings().mechanics();
        placeholders.put("death_delay", Integer.toString(Math.max(0, downed.getSecondsUntilDeath())));
        placeholders.put("invulnerability", Integer.toString(Math.max(0, downed.getInvulnerabilitySeconds())));
        placeholders.put("death_delay_bar", buildBar(
                "death-delay",
                downed.getSecondsUntilDeath(),
                Math.max(1, mechanics.deathDelaySeconds()),
                12,
                "&c",
                "&8"
        ));
        placeholders.put("invulnerability_bar", buildBar(
                "invulnerability",
                downed.getInvulnerabilitySeconds(),
                Math.max(1, mechanics.invulnerabilitySeconds()),
                8,
                "&e",
                "&8"
        ));
        placeholders.put("status", downedStatus(downed));
    }

    private void enrichRevivePlaceholders(Map<String, String> placeholders, ReviveSession session) {
        int progress = (int) Math.round(Math.min(100.0D, session.getProgressSeconds() / session.getRequiredSeconds() * 100.0D));
        placeholders.put("progress", Integer.toString(progress));
        placeholders.put("progress_bar", buildBar(
                "progress",
                session.getProgressSeconds(),
                Math.max(0.1D, session.getRequiredSeconds()),
                12,
                "&a",
                "&8"
        ));
    }

    private void enrichAutoRevivePlaceholders(Map<String, String> placeholders, DownedPlayer downed, double requiredSeconds) {
        int progress = (int) Math.round(Math.min(100.0D, downed.getAutoReviveProgressSeconds() / Math.max(0.1D, requiredSeconds) * 100.0D));
        placeholders.put("progress", Integer.toString(progress));
        placeholders.put("progress_bar", buildBar(
                "progress",
                downed.getAutoReviveProgressSeconds(),
                Math.max(0.1D, requiredSeconds),
                12,
                "&a",
                "&8"
        ));
    }

    private String downedStatus(DownedPlayer downed) {
        if (downed.getCarrierId() != null) {
            return plugin.getMessages().string("status.carried", "&3CARRIED");
        }
        if (downed.isAutoReviving()) {
            return plugin.getMessages().string("status.auto-revive", "&bAUTO-REVIVE");
        }
        if (downed.getInvulnerabilitySeconds() > 0) {
            return plugin.getMessages().string("status.protected", "&ePROTECTED");
        }
        return plugin.getMessages().string("status.critical", "&cCRITICAL");
    }

    private void playAutoReviveFinishEffect(Player player) {
        PluginSettings.Revive.AutoReviveFinishEffect effect = plugin.getPluginSettings().revive().autoReviveFinishEffect();
        if (effect == null || !effect.enabled()) {
            return;
        }

        Location location = player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        spawnConfiguredParticle(player, location, effect.primaryParticle());
        spawnConfiguredParticle(player, location, effect.secondaryParticle());

        PluginSettings.Sounds.ConfiguredSound configuredSound = effect.sound();
        if (configuredSound == null || !configuredSound.enabled()) {
            return;
        }

        Sound sound = resolveSound(configuredSound.sound());
        if (sound == null) {
            return;
        }

        player.getWorld().playSound(
                player.getLocation(),
                sound,
                SoundCategory.PLAYERS,
                configuredSound.volume(),
                configuredSound.pitch()
        );
    }

    private void spawnConfiguredParticle(Player player, Location location, PluginSettings.Revive.ConfiguredParticle configuredParticle) {
        if (configuredParticle == null || !configuredParticle.enabled()) {
            return;
        }

        Particle particle = resolveParticle(configuredParticle.particle());
        if (particle == null) {
            return;
        }

        player.getWorld().spawnParticle(
                particle,
                location,
                configuredParticle.count(),
                configuredParticle.offsetX(),
                configuredParticle.offsetY(),
                configuredParticle.offsetZ(),
                configuredParticle.extra()
        );
    }

    private Particle resolveParticle(String rawParticle) {
        if (rawParticle == null || rawParticle.isBlank()) {
            return null;
        }

        try {
            return Particle.valueOf(rawParticle.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String buildBar(String key, double current, double max, int size, String filledColor, String emptyColor) {
        if (size <= 0) {
            return "";
        }

        String basePath = "bars." + key + ".";
        int configuredSize = Math.max(1, plugin.getMessages().integer(basePath + "length", size));
        String filledToken = plugin.getMessages().string(basePath + "filled", "=");
        String emptyToken = plugin.getMessages().string(basePath + "empty", "-");
        String configuredFilledColor = plugin.getMessages().string(basePath + "filled-color", filledColor);
        String configuredEmptyColor = plugin.getMessages().string(basePath + "empty-color", emptyColor);
        String format = plugin.getMessages().string(basePath + "format", "&8[<filled><empty>&8]");

        double ratio = max <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, current / max));
        int filled = (int) Math.round(ratio * configuredSize);
        if (ratio > 0.0D && filled == 0) {
            filled = 1;
        }
        int empty = Math.max(0, configuredSize - filled);

        String filledText = configuredFilledColor + repeatToken(filledToken, filled);
        String emptyText = configuredEmptyColor + repeatToken(emptyToken, empty);
        return format
                .replace("<filled>", filledText)
                .replace("<empty>", emptyText);
    }

    private String repeatToken(String token, int amount) {
        if (amount <= 0) {
            return "";
        }
        String safeToken = token == null || token.isEmpty() ? "=" : token;
        return safeToken.repeat(amount);
    }

    private String formatDistance(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return "-";
        }
        return String.format(Locale.US, "%.1f", first.distance(second));
    }

    private void runConfiguredEvent(String path, Map<String, String> placeholders, Map<String, CommandSender> senders) {
        plugin.getEventActionsService().run(path, placeholders, senders);
    }

    private Map<String, String> eventPlaceholders(Player victim, Player attacker, Player reviver, Player picker) {
        Map<String, String> placeholders = placeholders(victim, attacker, reviver != null ? reviver : picker);
        if (victim != null) {
            placeholders.put("world", victim.getWorld().getName());
            placeholders.put("x", Integer.toString(victim.getLocation().getBlockX()));
            placeholders.put("y", Integer.toString(victim.getLocation().getBlockY()));
            placeholders.put("z", Integer.toString(victim.getLocation().getBlockZ()));
        } else {
            placeholders.put("world", "unknown");
            placeholders.put("x", "0");
            placeholders.put("y", "0");
            placeholders.put("z", "0");
        }
        return placeholders;
    }

    private Map<String, CommandSender> eventSenders(Player victim, Player attacker, Player reviver, Player picker) {
        Map<String, CommandSender> senders = new HashMap<>();
        if (victim != null) {
            senders.put("victim", victim);
        }
        if (attacker != null) {
            senders.put("attacker", attacker);
        }
        if (reviver != null) {
            senders.put("reviver", reviver);
        }
        if (picker != null) {
            senders.put("picker", picker);
        }
        return senders;
    }

    private void markForcedDismount(Player player) {
        forcedDismounts.add(player.getUniqueId());
        plugin.getSchedulerFacade().runEntityLater(
                player,
                () -> forcedDismounts.remove(player.getUniqueId()),
                () -> forcedDismounts.remove(player.getUniqueId()),
                2L
        );
    }

    private void playDownedEntryAnimation(Player victim, Player attacker, double incomingDamage) {
        playHurtAnimation(victim, attacker);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0F, 0.9F);
        victim.getWorld().spawnParticle(
                Particle.DAMAGE_INDICATOR,
                victim.getLocation().add(0.0D, 0.9D, 0.0D),
                Math.max(4, Math.min(10, (int) Math.round(Math.max(1.0D, incomingDamage)))),
                0.25D,
                0.2D,
                0.25D,
                0.0D
        );

        Vector impact = computeImpactVector(victim, attacker);
        if (impact.lengthSquared() > 0.0D) {
            victim.setVelocity(victim.getVelocity().multiply(0.25D).add(impact));
        }
    }

    private void playHurtAnimation(Player victim, Player attacker) {
        try {
            Method method = victim.getClass().getMethod("playHurtAnimation", float.class);
            method.invoke(victim, hurtYaw(victim, attacker));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private float hurtYaw(Player victim, Player attacker) {
        if (attacker == null) {
            return victim.getLocation().getYaw();
        }

        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (direction.lengthSquared() == 0.0D) {
            return victim.getLocation().getYaw();
        }

        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    private Vector computeImpactVector(Player victim, Player attacker) {
        PluginSettings.Mechanics mechanics = plugin.getPluginSettings().mechanics();
        if (attacker == null) {
            return new Vector(0.0D, mechanics.downedEntryKnockbackVertical(), 0.0D);
        }

        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        direction.setY(0.0D);
        if (direction.lengthSquared() > 0.0D) {
            direction.normalize().multiply(mechanics.downedEntryKnockbackHorizontal());
        }
        direction.setY(mechanics.downedEntryKnockbackVertical());
        return direction;
    }

    private void syncFakeBarrier(Player player) {
        Block aboveBlock = player.getLocation().getBlock().getRelative(BlockFace.UP);
        Location target = aboveBlock.getLocation();
        Location current = fakeBarrierLocations.get(player.getUniqueId());

        if (!aboveBlock.getType().isAir() && !aboveBlock.isPassable()) {
            if (current != null) {
                clearFakeBarrier(player);
            }
            return;
        }

        if (current != null) {
            if (!sameBlock(current, target)) {
                player.sendBlockChange(current, current.getBlock().getBlockData());
            }
        } else {
            fakeBarrierLocations.put(player.getUniqueId(), target);
        }

        fakeBarrierLocations.put(player.getUniqueId(), target);
        player.sendBlockChange(target, Material.BARRIER.createBlockData());
    }

    private void clearFakeBarrier(Player player) {
        Location fakeLocation = fakeBarrierLocations.remove(player.getUniqueId());
        if (fakeLocation != null) {
            player.sendBlockChange(fakeLocation, fakeLocation.getBlock().getBlockData());
        }
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() != null
                && second.getWorld() != null
                && first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private double maxHealth(Player player) {
        return player.getAttribute(Attribute.MAX_HEALTH) == null
                ? 20.0D
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private Map<String, String> placeholders(Player victim, Player attacker, Player actor) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("victim", victim == null ? "Desconocido" : victim.getName());
        placeholders.put("attacker", attacker == null ? "Desconocido" : attacker.getName());
        placeholders.put("reviver", actor == null ? "Desconocido" : actor.getName());
        placeholders.put("picker", actor == null ? "Desconocido" : actor.getName());
        if (victim != null) {
            Optional<ReviveZone> autoReviveZone = plugin.getZoneService().getAutoReviveZone(victim.getLocation());
            placeholders.put("auto_revive_zone", autoReviveZone.map(ReviveZone::name).orElse("none"));
            placeholders.put("in_auto_revive_zone", Boolean.toString(autoReviveZone.isPresent()));
        } else {
            placeholders.put("auto_revive_zone", "none");
            placeholders.put("in_auto_revive_zone", "false");
        }
        placeholders.put("auto_reviving", "false");
        return placeholders;
    }

    private void reapplyDownedState(Player player) {
        PluginSettings settings = plugin.getPluginSettings();
        player.leaveVehicle();
        player.setFallDistance(0.0F);
        player.setSprinting(false);
        player.setWalkSpeed((float) settings.mechanics().downedWalkSpeed());
        player.setHealth(Math.max(0.5D, Math.min(maxHealth(player), settings.mechanics().downedHealth())));
        applyConfiguredDownedEffects(player);
        applyDownedPose(player);
    }

    private void prepareTransientStateForDisconnect(Player player, boolean preserveDownedState) {
        stopRevive(player, false);
        cancelReviveForVictim(player.getUniqueId());
        cancelSuicide(player);
        plugin.getLootService().closeForPlayer(player);
        plugin.getLootService().closeByVictim(player, true, "loot.target-closed");
        dropCarried(player);
        markForcedDismount(player);
        player.leaveVehicle();
        restoreState(player);
        player.sendActionBar(Component.empty());

        DownedPlayer downed = downedPlayers.get(player.getUniqueId());
        if (downed != null) {
            downed.setActiveReviverId(null);
            downed.setCarrierId(null);
            if (!preserveDownedState) {
                downedPlayers.remove(player.getUniqueId());
            }
        }
        if (!preserveDownedState) {
            pendingFinalDeaths.remove(player.getUniqueId());
        }
        markStateDirty();
    }

    private void prepareStatesForShutdown() {
        reviveByReviver.values().forEach(session -> {
            if (session.getTask() != null) {
                session.getTask().cancel();
            }
        });
        reviveByReviver.clear();
        suicideTasks.values().forEach(NexusTask::cancel);
        suicideTasks.clear();
        forcedDismounts.clear();

        for (DownedPlayer downed : downedPlayers.values()) {
            downed.setActiveReviverId(null);
            downed.setCarrierId(null);
        }
        stateDirty = true;
    }

    private void markStateDirty() {
        stateDirty = true;
    }

    private void flushDirtyState() {
        if (!plugin.getPluginSettings().persistence().enabled()) {
            return;
        }
        if (!stateDirty) {
            return;
        }
        savePersistentState();
    }

    private void loadPersistentState() {
        if (!plugin.getPluginSettings().persistence().enabled()) {
            return;
        }
        if (!stateFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection section = config.getConfigurationSection("downed");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                UUID playerId = parseUuid(key);
                if (playerId == null) {
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }

                DownedPlayer downed = new DownedPlayer(
                        playerId,
                        parseUuid(entry.getString("attacker")),
                        Math.max(0, entry.getInt("seconds-until-death", plugin.getPluginSettings().mechanics().deathDelaySeconds())),
                        Math.max(0, entry.getInt("invulnerability-seconds", plugin.getPluginSettings().mechanics().invulnerabilitySeconds()))
                );
                downedPlayers.put(playerId, downed);
            }
        }

        for (String rawUuid : config.getStringList("pending-final-deaths")) {
            UUID playerId = parseUuid(rawUuid);
            if (playerId != null) {
                pendingFinalDeaths.add(playerId);
            }
        }
        stateDirty = false;
    }

    private void savePersistentState() {
        if (!plugin.getPluginSettings().persistence().enabled()) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, DownedPlayer> entry : downedPlayers.entrySet()) {
            String path = "downed." + entry.getKey();
            DownedPlayer downed = entry.getValue();
            config.set(path + ".attacker", downed.getAttackerId() == null ? null : downed.getAttackerId().toString());
            config.set(path + ".seconds-until-death", downed.getSecondsUntilDeath());
            config.set(path + ".invulnerability-seconds", downed.getInvulnerabilitySeconds());
        }

        config.set(
                "pending-final-deaths",
                pendingFinalDeaths.stream()
                        .map(UUID::toString)
                        .toList()
        );

        try {
            config.save(stateFile);
            stateDirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("No se pudo guardar states.yml: " + exception.getMessage());
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

