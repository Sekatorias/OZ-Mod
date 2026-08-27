package dev.oz.zaruba;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;

/** Replaces the hundreds of generated border timing functions with direct arithmetic. */
public final class BorderBridge {
    private final OzConfig config;

    public BorderBridge(OzConfig config) {
        this.config = config;
    }

    public int refresh(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        WorldBorder border = world.getWorldBorder();

        int target = ScoreboardBridge.get(server, "ozSBSize", "OZ", config.match.defaultFinalSize);
        int base = ScoreboardBridge.get(server, "ozBSize", "OZ", config.match.defaultArenaSize);
        int durationMinutes = ScoreboardBridge.get(server, "ozSBDur", "OZ", config.match.shrinkMinutes);
        int speedPct = Math.max(1, ScoreboardBridge.get(server, "ozPressure", "OZSpeedPct", 100));

        double now = border.getSize();
        if (now <= target + 1.0) {
            border.setSize(target);
            ScoreboardBridge.set(server, "ozEnabled", "OZBorderDone", 1);
            return 1;
        }

        double baseDistance = Math.max(1.0, base - target);
        double remainingDistance = Math.max(0.0, now - target);
        double seconds = remainingDistance / baseDistance * durationMinutes * 60.0 * 100.0 / speedPct;
        long millis = Math.max(1000L, Math.round(seconds * 1000.0));

        border.interpolateSize(now, target, millis);
        ScoreboardBridge.set(server, "ozEnabled", "OZBorderDone", 0);
        return 1;
    }
}
