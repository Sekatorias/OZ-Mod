package dev.oz.zaruba;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;

public final class ScoreboardBridge {
    private ScoreboardBridge() {}

    public static int get(MinecraftServer server, String objectiveName, String holder, int fallback) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(objectiveName);
        if (objective == null || !scoreboard.playerHasObjective(holder, objective)) return fallback;
        return scoreboard.getPlayerScore(holder, objective).getScore();
    }

    public static void set(MinecraftServer server, String objectiveName, String holder, int value) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(objectiveName);
        if (objective == null) return;
        scoreboard.getPlayerScore(holder, objective).setScore(value);
    }
}
