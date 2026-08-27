package dev.oz.zaruba;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class OzCommands {
    private OzCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                ZoneSearchManager zones,
                                BorderBridge border,
                                OzConfig config) {
        dispatcher.register(CommandManager.literal("oz")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("zone")
                        .then(CommandManager.literal("search")
                                .executes(ctx -> start(ctx.getSource(), zones, null))
                                .then(CommandManager.literal("any")
                                        .executes(ctx -> start(ctx.getSource(), zones, ZoneSearchManager.Filter.ANY)))
                                .then(CommandManager.literal("water")
                                        .executes(ctx -> start(ctx.getSource(), zones, ZoneSearchManager.Filter.WATER)))
                                .then(CommandManager.literal("mountain")
                                        .executes(ctx -> start(ctx.getSource(), zones, ZoneSearchManager.Filter.MOUNTAIN)))
                                .then(CommandManager.literal("mixed")
                                        .executes(ctx -> start(ctx.getSource(), zones, ZoneSearchManager.Filter.MIXED))))
                        .then(CommandManager.literal("cancel")
                                .executes(ctx -> zones.cancel(ctx.getSource().getServer(), false))
                                .then(CommandManager.literal("silent")
                                        .executes(ctx -> zones.cancel(ctx.getSource().getServer(), true))))
                        .then(CommandManager.literal("status")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(() -> Text.literal("OZ zone search: " + zones.status()), false);
                                    return 1;
                                }))
                        .then(CommandManager.literal("origin")
                                .then(CommandManager.literal("ensure")
                                        .executes(ctx -> {
                                            ServerPlayerEntity p = ctx.getSource().getPlayer();
                                            if (p == null) return 0;
                                            return zones.ensureOrigin(p);
                                        }))
                                .then(CommandManager.literal("reset")
                                        .executes(ctx -> {
                                            ServerPlayerEntity p = ctx.getSource().getPlayer();
                                            if (p == null) return 0;
                                            return zones.resetOrigin(p);
                                        }))))
                .then(CommandManager.literal("border")
                        .then(CommandManager.literal("refresh")
                                .executes(ctx -> border.refresh(ctx.getSource().getServer()))))
                .then(CommandManager.literal("config")
                        .then(CommandManager.literal("apply")
                                .executes(ctx -> ConfigBridge.apply(ctx.getSource().getServer(), config)))
                        .then(CommandManager.literal("show")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "OZ: финал " + config.match.finalStartMinutes + "м • сужение " + config.match.shrinkMinutes
                                                    + "м • овертайм " + config.match.overtimeStartMinutes + "м • sudden death "
                                                    + config.match.suddenDeathMinutes + "м").formatted(Formatting.AQUA), false);
                                    return 1;
                                })))
                .then(CommandManager.literal("info")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("OZ mod 0.1.0 • fast biome-source arena search • direct border math • late-game vertical pressure")
                                    .formatted(Formatting.LIGHT_PURPLE), false);
                            return 1;
                        })));
    }

    private static int start(ServerCommandSource source, ZoneSearchManager zones, ZoneSearchManager.Filter filter) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Поиск зоны нужно запускать от имени игрока."));
            return 0;
        }
        return zones.start(player, filter);
    }
}
