package dev.oz.zaruba;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OZ Storm: a late-match vertical danger field around the playable surface.
 * It preserves flight and digging as combat tools, but prevents indefinite
 * stalling far above or below opponents.
 */
public final class ArenaPressureManager {
    private final OzConfig config;
    private final Map<UUID, Integer> violationSeconds = new HashMap<>();
    private int tickCounter;

    public ArenaPressureManager(OzConfig config) {
        this.config = config;
    }

    public void tick(MinecraftServer server) {
        if (!config.pressure.enabled) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        int state = ScoreboardBridge.get(server, "ozState", "OZ", 0);
        if (state != 4) {
            violationSeconds.clear();
            return;
        }

        int matchTicks = ScoreboardBridge.get(server, "ozMatchTick", "OZ", 0);
        double minute = matchTicks / 1200.0;
        if (minute < config.pressure.startMinute) {
            violationSeconds.clear();
            return;
        }

        double stormStrength = (minute - config.pressure.startMinute)
                / Math.max(1.0, config.pressure.fullStrengthMinute - config.pressure.startMinute);
        stormStrength = Math.max(0.0, Math.min(1.0, stormStrength));

        int skyClearance = lerp(config.pressure.skyClearanceStart, config.pressure.skyClearanceEnd, stormStrength);
        int depthClearance = lerp(config.pressure.depthClearanceStart, config.pressure.depthClearanceEnd, stormStrength);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isSpectator() || !player.getCommandTags().contains("playing")) {
                violationSeconds.remove(player.getUuid());
                continue;
            }

            ServerWorld world = player.getServerWorld();
            if (!world.getRegistryKey().equals(World.OVERWORLD)) {
                violationSeconds.remove(player.getUuid());
                continue;
            }

            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
            double relative = player.getY() - surfaceY;
            boolean sky = relative > skyClearance;
            boolean depth = relative < -depthClearance;

            if (!sky && !depth) {
                violationSeconds.remove(player.getUuid());
                continue;
            }

            int seconds = violationSeconds.merge(player.getUuid(), 1, Integer::sum);
            double overflow = sky ? relative - skyClearance : (-relative) - depthClearance;
            String side = sky ? "Шторм над ареной" : "Шторм под ареной";
            int limit = sky ? skyClearance : depthClearance;

            if (seconds >= config.pressure.warningSeconds) {
                player.sendMessage(Text.literal("⚠ " + side + " • вернись к поверхности (предел ≈ " + limit + " блоков)")
                        .formatted(seconds >= config.pressure.damageAfterSeconds ? Formatting.RED : Formatting.GOLD), true);
                server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),
                        "effect give @s minecraft:glowing 2 0 true");
            }

            // The storm pushes immediately after the warning and gets stronger with
            // match progression and distance outside the safe vertical band.
            if (seconds >= config.pressure.warningSeconds) {
                Vec3d v = player.getVelocity();
                double distanceFactor = Math.min(1.0, overflow / 24.0);
                double push = 0.16 + 0.22 * stormStrength + 0.22 * distanceFactor;
                double newY = sky
                        ? Math.min(v.y, -push)
                        : Math.max(v.y, push);
                player.setVelocity(v.x, newY, v.z);
                player.velocityModified = true;
            }

            if (seconds >= config.pressure.damageAfterSeconds) {
                double distanceFactor = Math.min(1.5, overflow / 20.0);
                double damage = config.pressure.damagePerSecond * (0.75 + 0.50 * stormStrength + 0.35 * distanceFactor);
                player.damage(world.getDamageSources().magic(), (float) damage);
            }
        }
    }

    private static int lerp(int start, int end, double t) {
        return (int) Math.round(start + (end - start) * t);
    }
}
