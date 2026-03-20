package com.aquiles.nexusrevive.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class NexusBoard {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] ENTRIES = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74",
            "\u00A75", "\u00A76", "\u00A77", "\u00A78", "\u00A79",
            "\u00A7a", "\u00A7b", "\u00A7c", "\u00A7d", "\u00A7e"
    };

    private final Player player;
    private final Scoreboard previousScoreboard;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<String> activeTeamNames = new ArrayList<>();
    private boolean deleted;

    public NexusBoard(Player player) {
        this.player = Objects.requireNonNull(player, "player");
        this.previousScoreboard = player.getScoreboard();
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("nr_" + shortId(player.getUniqueId()), Criteria.DUMMY, Component.empty());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(scoreboard);
    }

    public void updateTitle(Component title) {
        if (deleted) {
            return;
        }
        objective.displayName(title);
    }

    public void updateLines(List<Component> lines) {
        if (deleted) {
            return;
        }

        int size = Math.min(lines.size(), ENTRIES.length);
        cleanupUnusedTeams(size);

        for (int index = 0; index < size; index++) {
            String teamName = teamName(index);
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                String entry = ENTRIES[index];
                team.addEntry(entry);
                objective.getScore(entry).setScore(size - index);
            }

            team.prefix(lines.get(index));
            if (!activeTeamNames.contains(teamName)) {
                activeTeamNames.add(teamName);
            }
        }
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void delete() {
        if (deleted) {
            return;
        }
        deleted = true;

        for (String teamName : new ArrayList<>(activeTeamNames)) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        }
        activeTeamNames.clear();

        objective.unregister();

        if (player.isOnline() && player.getScoreboard() == scoreboard) {
            player.setScoreboard(previousScoreboard == null ? Bukkit.getScoreboardManager().getMainScoreboard() : previousScoreboard);
        }
    }

    private void cleanupUnusedTeams(int needed) {
        for (int index = activeTeamNames.size() - 1; index >= 0; index--) {
            if (index < needed) {
                continue;
            }
            String teamName = activeTeamNames.remove(index);
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                for (String entry : team.getEntries()) {
                    scoreboard.resetScores(entry);
                }
                team.unregister();
            }
        }
    }

    private String teamName(int index) {
        return "nr_line_" + index;
    }

    private String shortId(UUID uuid) {
        return LEGACY.serialize(Component.text(uuid.toString().replace("-", ""))).substring(0, 8);
    }
}
