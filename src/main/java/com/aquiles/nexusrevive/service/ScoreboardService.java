package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.scoreboard.NexusBoard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ScoreboardService {
    private final NexusRevivePlugin plugin;
    private final Map<UUID, NexusBoard> boards = new HashMap<>();
    private BukkitTask updateTask;

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
        NexusBoard board = boards.remove(player.getUniqueId());
        if (board != null && !board.isDeleted()) {
            board.delete();
        }
    }

    private void start() {
        if (!plugin.getPluginSettings().scoreboard().enabled()) {
            return;
        }
        long interval = Math.max(1L, plugin.getPluginSettings().scoreboard().updateIntervalTicks());
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, interval);
    }

    private void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void tick() {
        if (!plugin.getPluginSettings().scoreboard().enabled()) {
            clearAll();
            return;
        }

        Set<UUID> activeBoards = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) {
                clear(player);
                continue;
            }

            BoardView view = resolveView(player);
            if (view == null) {
                clear(player);
                continue;
            }

            activeBoards.add(player.getUniqueId());
            NexusBoard board = boards.computeIfAbsent(player.getUniqueId(), ignored -> new NexusBoard(player));
            updateBoard(board, view);
        }

        Set<UUID> staleBoards = new HashSet<>(boards.keySet());
        staleBoards.removeAll(activeBoards);
        for (UUID uuid : staleBoards) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                clear(player);
                continue;
            }
            NexusBoard board = boards.remove(uuid);
            if (board != null && !board.isDeleted()) {
                board.delete();
            }
        }
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

