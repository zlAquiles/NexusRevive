package com.aquiles.nexusrevive.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public final class NexusScheduler {
    private final Plugin plugin;

    public NexusScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public NexusTask runGlobal(Runnable runnable) {
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> runnable.run());
        return task::cancel;
    }

    public NexusTask runGlobalLater(Runnable runnable, long delayTicks) {
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> runnable.run(), Math.max(1L, delayTicks));
        return task::cancel;
    }

    public NexusTask runGlobalTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> runnable.run(),
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks)
        );
        return task::cancel;
    }

    public NexusTask runAsync(Runnable runnable) {
        ScheduledTask task = Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> runnable.run());
        return task::cancel;
    }

    public NexusTask runAsyncTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> runnable.run(),
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
        );
        return task::cancel;
    }

    public NexusTask runEntityNow(Entity entity, Runnable runnable, Runnable retired) {
        return runEntityLater(entity, runnable, retired, 1L);
    }

    public NexusTask runEntityLater(Entity entity, Runnable runnable, Runnable retired, long delayTicks) {
        ScheduledTask task = entity.getScheduler().runDelayed(
                plugin,
                scheduledTask -> runnable.run(),
                retired,
                Math.max(1L, delayTicks)
        );
        return task == null ? NexusTask.NOOP : task::cancel;
    }

    public NexusTask runEntityTimer(Entity entity, Runnable runnable, Runnable retired, long initialDelayTicks, long periodTicks) {
        ScheduledTask task = entity.getScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> runnable.run(),
                retired,
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks)
        );
        return task == null ? NexusTask.NOOP : task::cancel;
    }

    public NexusTask runRegionLater(Location location, Runnable runnable, long delayTicks) {
        ScheduledTask task = Bukkit.getRegionScheduler().runDelayed(
                plugin,
                location,
                scheduledTask -> runnable.run(),
                Math.max(1L, delayTicks)
        );
        return task::cancel;
    }

    public NexusTask runRegionTimer(Location location, Runnable runnable, long initialDelayTicks, long periodTicks) {
        ScheduledTask task = Bukkit.getRegionScheduler().runAtFixedRate(
                plugin,
                location,
                scheduledTask -> runnable.run(),
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks)
        );
        return task::cancel;
    }

    public boolean isFolia() {
        return Bukkit.getServer().getName().toLowerCase().contains("folia");
    }

    private long ticksToMillis(long ticks) {
        return Math.max(1L, ticks) * 50L;
    }
}
