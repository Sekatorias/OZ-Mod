package dev.oz.zaruba;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.border.WorldBorder;

/**
 * Makes the default ruleset converge instead of turning into an endless survival match.
 * Default pacing: 18m development, 24m hunt/shrink, then a short hard final.
 */
public final class MatchPacingManager {
    private final OzConfig config;
    private boolean overtimeStarted;
    private boolean hardCollapseStarted;
    private int tickCounter;

    public MatchPacingManager(OzConfig config) {
        this.config = config;
    }

    public void tick(MinecraftServer server) {
        if (++tickCounter < 20) return;
        tickCounter = 0;

        int state = ScoreboardBridge.get(server, "ozState", "OZ", 0);
        if (state != 4) {
            overtimeStarted = false;
            hardCollapseStarted = false;
            return;
        }

        int matchTicks = ScoreboardBridge.get(server, "ozMatchTick", "OZ", 0);
        int seconds = matchTicks / 20;
        ServerWorld world = server.getOverworld();
        WorldBorder border = world.getWorldBorder();

        if (!overtimeStarted && seconds >= config.match.overtimeStartMinutes * 60) {
            overtimeStarted = true;
            double now = border.getSize();
            border.interpolateSize(now, Math.min((double) config.match.overtimeBorderSize, now), (long) config.match.overtimeShrinkMinutes * 60L * 1000L);
            broadcast(server,
                    Text.literal("ОВЕРТАЙМ").formatted(Formatting.RED, Formatting.BOLD),
                    Text.literal("Шторм усиливается • арена переходит в жёсткий финал").formatted(Formatting.GOLD));
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withLevel(4),
                    "bossbar set oz:timer name {\"text\":\"OZ | ОВЕРТАЙМ • К ЦЕНТРУ\",\"color\":\"red\",\"bold\":true}");
        }

        // Late final: everyone is periodically revealed. Flight remains useful, stalling does not.
        if (seconds >= config.match.revealAllMinutes * 60 && seconds % 2 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getCommandTags().contains("playing") && !player.isSpectator()) {
                    server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),
                            "effect give @s minecraft:glowing 3 0 true");
                }
            }
        }

        if (!hardCollapseStarted && seconds >= config.match.suddenDeathMinutes * 60) {
            hardCollapseStarted = true;
            border.setSize(Math.min((double) config.match.suddenDeathBorderSize, border.getSize()));
            broadcast(server,
                    Text.literal("РЕШАЮЩАЯ СХВАТКА").formatted(Formatting.DARK_RED, Formatting.BOLD),
                    Text.literal(config.match.suddenDeathBorderSize + "×" + config.match.suddenDeathBorderSize + " • все игроки видимы • шторм максимальной силы").formatted(Formatting.RED));
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withLevel(4),
                    "bossbar set oz:timer name {\"text\":\"OZ | РЕШАЮЩАЯ СХВАТКА\",\"color\":\"dark_red\",\"bold\":true}");
        }
    }

    private static void broadcast(MinecraftServer server, Text title, Text subtitle) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("[OZ] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD).append(subtitle), false);
            server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),
                    "title @s title " + Text.Serializer.toJson(title));
            server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),
                    "title @s subtitle " + Text.Serializer.toJson(subtitle));
        }
    }
}
