package dev.oz.zaruba;

import net.minecraft.server.MinecraftServer;

/** Applies Java config defaults to the legacy scoreboard-backed lobby UI. */
public final class ConfigBridge {
    private ConfigBridge() {}

    public static int apply(MinecraftServer server, OzConfig config) {
        ScoreboardBridge.set(server, "ozBSize", "OZ", config.match.defaultArenaSize);
        ScoreboardBridge.set(server, "ozSBStart", "OZ", config.match.finalStartMinutes);
        ScoreboardBridge.set(server, "ozSBSize", "OZ", config.match.defaultFinalSize);
        ScoreboardBridge.set(server, "ozSBDur", "OZ", config.match.shrinkMinutes);
        ScoreboardBridge.set(server, "ozGlow", "OZ", config.match.glowStartMinutes);
        ScoreboardBridge.set(server, "ozGlowEnd", "OZ", config.match.glowEndMinutes);
        ScoreboardBridge.set(server, "ozCenterStart", "OZ", config.match.centerPressureStartMinutes);
        ScoreboardBridge.set(server, "ozCenterEnd", "OZ", config.match.centerPressureEndMinutes);
        return 1;
    }
}
