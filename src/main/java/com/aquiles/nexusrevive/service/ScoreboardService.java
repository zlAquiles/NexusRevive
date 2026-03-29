package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.scheduler.NexusTask;
import com.aquiles.nexusrevive.scoreboard.NexusBoard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardService {
    private final NexusRevivePlugin plugin;
    private final Map<UUID, NexusBoard> boards = new HashMap<>();
    private final Map<UUID, NexusTask> updateTasks = new HashMap<>();

    public ScoreboardService(NexusRevivePlugin plugin) {
        this.plugin = plugin;
        start();
    }

    public void reload() {
        stop();
        clearAll();
        start();
    }

    public void shutdown() {
        stop();
        clearAll();
    }

    public void clear(Player player) {
        NexusTask task = updateTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        clearBoard(player);
    }

    private void clearBoard(Player player) {
        NexusBoard board = boards.remove(player.getUniqueId());
        if (board != null && !board.isDeleted()) {
            board.delete();
        }
    }

    private void start() {
        if (!plugin.getPluginSettings().scoreboard().enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            startTracking(player);
        }
    }

    private void stop() {
        for (NexusTask task : updateTasks.values()) {
            task.cancel();
        }
        updateTasks.clear();
    }

    public void handleJoin(Player player) {
        if (!plugin.getPluginSettings().scoreboard().enabled()) {
            return;
        }
        startTracking(player);
    }

    private void startTracking(Player player) {
        NexusTask existing = updateTasks.remove(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }

        long interval = Math.max(1L, plugin.getPluginSettings().scoreboard().updateIntervalTicks());
        updateTasks.put(
                player.getUniqueId(),
                plugin.getSchedulerFacade().runEntityTimer(
                        player,
                        () -> tick(player),
                        () -> clear(player),
                        1L,
                        interval
                )
        );
    }

    private void tick(Player player) {
        if (!plugin.getPluginSettings().scoreboard().enabled() || !player.isOnline() || player.isDead()) {
            clear(player);
            return;
        }

        BoardView view = resolveView(player);
        if (view == null) {
            clearBoard(player);
            return;
        }

        NexusBoard board = boards.computeIfAbsent(player.getUniqueId(), ignored -> new NexusBoard(player));
        updateBoard(board, view);
    }

    private void updateBoard(NexusBoard board, BoardView view) {
        board.updateTitle(view.title());
        board.updateLines(view.lines());
    }

    private void clearAll() {
        for (NexusBoard board : boards.values()) {
            if (!board.isDeleted()) {
                board.delete();
            }
        }
        boards.clear();
    }

    private BoardView resolveView(Player player) {
        if (plugin.getDownedService().isDowned(player) && plugin.getPluginSettings().scoreboard().showForDowned()) {
            Map<String, String> placeholders = plugin.getDownedService().createVictimHudPlaceholders(player);
            if (placeholders.isEmpty()) {
                return null;
            }
            String linesPath = plugin.getDownedService().isBeingRevived(player)
                    || plugin.getDownedService().isAutoReviving(player)
                    ? "scoreboard.victim-reviving"
                    : "scoreboard.victim-waiting";
            return new BoardView(
                    plugin.getMessages().component("scoreboard.victim-title", placeholders),
                    plugin.getMessages().components(linesPath, placeholders)
            );
        }

        if (plugin.getPluginSettings().scoreboard().showForReviver()
                && plugin.getDownedService().getReviveSession(player).isPresent()) {
            Map<String, String> placeholders = plugin.getDownedService().createReviverHudPlaceholders(player);
            if (placeholders.isEmpty()) {
                return null;
            }
            return new BoardView(
                    plugin.getMessages().component("scoreboard.reviver-title", placeholders),
                    plugin.getMessages().components("scoreboard.reviver-active", placeholders)
            );
        }

        if (plugin.getPluginSettings().scoreboard().showForPicker()
                && plugin.getDownedService().getCarriedVictim(player).isPresent()) {
            Map<String, String> placeholders = plugin.getDownedService().createPickerHudPlaceholders(player);
            if (placeholders.isEmpty()) {
                return null;
            }
            return new BoardView(
                    plugin.getMessages().component("scoreboard.picker-title", placeholders),
                    plugin.getMessages().components("scoreboard.picker-carrying", placeholders)
            );
        }

        return null;
    }

    private record BoardView(net.kyori.adventure.text.Component title, List<net.kyori.adventure.text.Component> lines) {
    }
}

