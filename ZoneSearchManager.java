package dev.oz.zaruba;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.Set;
import java.util.UUID;

/**
 * Arena search that samples the generator's biome source directly.
 * No marker entities, no forceload tickets and no chunk generation are required
 * just to score a candidate.
 */
public final class ZoneSearchManager {
    public enum Filter {
        ANY, WATER, MOUNTAIN, MIXED;

        static Filter fromScore(int score) {
            return switch (score) {
                case 1 -> WATER;
                case 2 -> MOUNTAIN;
                case 3 -> MIXED;
                default -> ANY;
            };
        }
    }

    private static final Set<String> WATER = Set.of(
            "minecraft:ocean", "minecraft:deep_ocean", "minecraft:frozen_ocean",
            "minecraft:deep_frozen_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
            "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:warm_ocean",
            "minecraft:river", "minecraft:frozen_river"
    );

    private static final Set<String> MOUNTAIN = Set.of(
            "minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks",
            "minecraft:meadow", "minecraft:grove", "minecraft:snowy_slopes",
            "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
            "minecraft:windswept_forest", "minecraft:cherry_grove"
    );

    private final OzConfig config;
    private SearchJob job;

    public ZoneSearchManager(OzConfig config) {
        this.config = config;
    }

    public boolean isRunning() {
        return job != null;
    }

    public int start(ServerPlayerEntity requester, Filter explicitFilter) {
        if (job != null) {
            requester.sendMessage(prefix().append(Text.literal("Поиск зоны уже идёт.").formatted(Formatting.RED)), false);
            return 0;
        }

        MinecraftServer server = requester.getServer();
        if (server == null) return 0;
        ServerWorld world = server.getOverworld();
        ZoneHistoryStore history = ZoneHistoryStore.forWorld(world);
        history.ensureOrigin(requester.getBlockX(), requester.getBlockZ());

        int arenaSize = ScoreboardBridge.get(server, "ozBSize", "OZ", config.match.defaultArenaSize);
        Filter filter = explicitFilter != null
                ? explicitFilter
                : Filter.fromScore(ScoreboardBridge.get(server, "ozZone", "ZFilter", 0));

        job = new SearchJob(
                requester.getUuid(), world, history,
                history.originX(), history.originZ(), history.nextIndex(),
                Math.max(128, arenaSize), filter
        );

        ScoreboardBridge.set(server, "ozZone", "ZState", 1);
        ScoreboardBridge.set(server, "ozZone", "ZTry", 0);
        requester.sendMessage(prefix()
                .append(Text.literal("Быстрый поиск зоны: ").formatted(Formatting.AQUA))
                .append(Text.literal(filterName(filter)).formatted(Formatting.GOLD))
                .append(Text.literal(" • без генерации чанков").formatted(Formatting.GRAY)), false);
        return 1;
    }

    public int cancel(MinecraftServer server, boolean silent) {
        if (job == null) {
            ScoreboardBridge.set(server, "ozZone", "ZState", 0);
            return 0;
        }
        ServerPlayerEntity requester = server.getPlayerManager().getPlayer(job.requester);
        job.history.flush();
        job = null;
        ScoreboardBridge.set(server, "ozZone", "ZState", 0);
        if (!silent && requester != null) {
            requester.sendMessage(prefix().append(Text.literal("Поиск зоны отменён.").formatted(Formatting.YELLOW)), false);
        }
        return 1;
    }

    public int ensureOrigin(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        ZoneHistoryStore store = ZoneHistoryStore.forWorld(world);
        store.ensureOrigin(player.getBlockX(), player.getBlockZ());
        return 1;
    }

    public int resetOrigin(ServerPlayerEntity player) {
        if (job != null) cancel(player.getServer(), true);
        ZoneHistoryStore store = ZoneHistoryStore.forWorld(player.getServerWorld());
        store.resetOrigin(player.getBlockX(), player.getBlockZ());
        ScoreboardBridge.set(player.getServer(), "ozZone", "ZIndex", 0);
        player.sendMessage(prefix().append(Text.literal("История зон сброшена. Новая сетка начинается здесь.").formatted(Formatting.GOLD)), false);
        return 1;
    }

    public void tick(MinecraftServer server) {
        if (job == null) return;
        for (int i = 0; i < config.zoneSearch.candidatesPerTick && job != null; i++) {
            evaluateNext(server);
        }
    }

    private void evaluateNext(MinecraftServer server) {
        SearchJob j = job;
        if (j == null) return;
        ServerPlayerEntity requester = server.getPlayerManager().getPlayer(j.requester);
        if (requester == null) {
            cancel(server, true);
            return;
        }

        if (j.checked >= config.zoneSearch.maxCandidates || j.index > 256) {
            finishBest(server, requester, j);
            return;
        }

        int index = j.index++;
        int[] cell = spiralCell(index);
        int centerX = j.originX + cell[0] * config.zoneSearch.candidateSpacing;
        int centerZ = j.originZ + cell[1] * config.zoneSearch.candidateSpacing;
        Candidate candidate = sample(j.world, centerX, centerZ, j.arenaSize, j.filter);
        candidate = new Candidate(index, centerX, centerZ, candidate.water, candidate.mountain, candidate.score, candidate.pass);
        j.checked++;
        j.history.advanceTo(j.index);
        if ((j.checked & 7) == 0) j.history.flush();

        ScoreboardBridge.set(server, "ozZone", "ZTry", j.checked);
        ScoreboardBridge.set(server, "ozZone", "ZIndex", index);
        ScoreboardBridge.set(server, "ozZone", "ZWater", candidate.water);
        ScoreboardBridge.set(server, "ozZone", "ZMountain", candidate.mountain);

        if (j.best == null || candidate.score > j.best.score) {
            j.best = candidate;
        }

        requester.sendMessage(Text.literal("OZ • зона " + j.checked + "/" + config.zoneSearch.maxCandidates
                + "  вода " + candidate.water + "/" + sampleCount()
                + "  горы " + candidate.mountain + "/" + sampleCount())
                .formatted(Formatting.DARK_GRAY), true);

        if (j.filter == Filter.ANY || candidate.pass) {
            accept(server, requester, j, candidate, false);
        }
    }

    private Candidate sample(ServerWorld world, int centerX, int centerZ, int arenaSize, Filter filter) {
        BiomeSource biomeSource = world.getChunkManager().getChunkGenerator().getBiomeSource();
        MultiNoiseUtil.MultiNoiseSampler sampler = world.getChunkManager().getNoiseConfig().getMultiNoiseSampler();
        int axis = config.zoneSearch.samplesPerAxis;
        double span = arenaSize * 0.80;
        double start = -span / 2.0;
        double step = axis <= 1 ? 0.0 : span / (axis - 1);
        int water = 0;
        int mountain = 0;

        for (int ix = 0; ix < axis; ix++) {
            for (int iz = 0; iz < axis; iz++) {
                int x = centerX + (int) Math.round(start + ix * step);
                int z = centerZ + (int) Math.round(start + iz * step);
                String low = biomeId(biomeSource.getBiome(x >> 2, 64 >> 2, z >> 2, sampler));
                String high = biomeId(biomeSource.getBiome(x >> 2, 120 >> 2, z >> 2, sampler));
                if (WATER.contains(low)) water++;
                if (MOUNTAIN.contains(high)) mountain++;
            }
        }

        int total = axis * axis;
        int waterNeed = (int) Math.ceil(total * config.zoneSearch.waterThreshold);
        int mountainNeed = (int) Math.ceil(total * config.zoneSearch.mountainThreshold);
        int mixedNeed = (int) Math.ceil(total * config.zoneSearch.mixedThreshold);
        boolean pass = switch (filter) {
            case ANY -> true;
            case WATER -> water >= waterNeed;
            case MOUNTAIN -> mountain >= mountainNeed;
            case MIXED -> water >= mixedNeed && mountain >= mixedNeed;
        };
        int score = switch (filter) {
            case ANY, WATER -> water;
            case MOUNTAIN -> mountain;
            case MIXED -> Math.min(water, mountain);
        };
        return new Candidate(0, centerX, centerZ, water, mountain, score, pass);
    }

    private void finishBest(MinecraftServer server, ServerPlayerEntity requester, SearchJob j) {
        if (j.best == null) {
            requester.sendMessage(prefix().append(Text.literal("Не удалось оценить ни одной зоны.").formatted(Formatting.RED)), false);
            cancel(server, true);
            return;
        }
        accept(server, requester, j, j.best, true);
    }

    private void accept(MinecraftServer server, ServerPlayerEntity requester, SearchJob j, Candidate candidate, boolean fallback) {
        j.history.flush();
        job = null;
        ScoreboardBridge.set(server, "ozZone", "ZState", 0);
        ScoreboardBridge.set(server, "ozZone", "ZCandidate", candidate.index);
        ScoreboardBridge.set(server, "ozZone", "ZIndex", candidate.index);

        Text line = prefix()
                .append(Text.literal(fallback ? "Идеальной зоны нет — выбран лучший кандидат " : "Зона найдена: ")
                        .formatted(fallback ? Formatting.GOLD : Formatting.GREEN))
                .append(Text.literal("#" + candidate.index).formatted(Formatting.YELLOW))
                .append(Text.literal(" • вода " + candidate.water + "/" + sampleCount()
                        + " • горы " + candidate.mountain + "/" + sampleCount()).formatted(Formatting.GRAY));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) p.sendMessage(line, false);

        ScoreboardBridge.set(server, "ozZone", "ZAutoSetup", 1);
        Vec3d pos = new Vec3d(candidate.centerX + 0.5, 100, candidate.centerZ + 0.5);
        server.getCommandManager().executeWithPrefix(
                requester.getCommandSource().withWorld(j.world).withPosition(pos).withLevel(4),
                "function oz:setup"
        );
    }

    public String status() {
        SearchJob j = job;
        if (j == null) return "idle";
        return "running: " + j.checked + "/" + config.zoneSearch.maxCandidates + ", filter=" + j.filter;
    }

    private int sampleCount() {
        return config.zoneSearch.samplesPerAxis * config.zoneSearch.samplesPerAxis;
    }

    private static String biomeId(RegistryEntry<Biome> biome) {
        return biome.getKey()
                .map(RegistryKey::getValue)
                .map(Identifier::toString)
                .orElse("minecraft:unknown");
    }

    /** Square spiral matching the old 1..256 sector order. */
    static int[] spiralCell(int index) {
        if (index <= 0) return new int[]{0, 0};
        int x = 0, z = 0, n = 0;
        int stepLen = 1;
        while (n < index) {
            for (int i = 0; i < stepLen && n < index; i++) { x++; n++; }
            for (int i = 0; i < stepLen && n < index; i++) { z++; n++; }
            stepLen++;
            for (int i = 0; i < stepLen && n < index; i++) { x--; n++; }
            for (int i = 0; i < stepLen && n < index; i++) { z--; n++; }
            stepLen++;
        }
        return new int[]{x, z};
    }

    private static String filterName(Filter f) {
        return switch (f) {
            case ANY -> "Любая";
            case WATER -> "Вода";
            case MOUNTAIN -> "Горы";
            case MIXED -> "Смешанная";
        };
    }

    private static MutableText prefix() {
        return Text.literal("[OZ] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);
    }

    private static final class SearchJob {
        final UUID requester;
        final ServerWorld world;
        final ZoneHistoryStore history;
        final int originX;
        final int originZ;
        final int arenaSize;
        final Filter filter;
        int index;
        int checked;
        Candidate best;

        SearchJob(UUID requester, ServerWorld world, ZoneHistoryStore history,
                  int originX, int originZ, int index, int arenaSize, Filter filter) {
            this.requester = requester;
            this.world = world;
            this.history = history;
            this.originX = originX;
            this.originZ = originZ;
            this.index = index;
            this.arenaSize = arenaSize;
            this.filter = filter;
        }
    }

    private record Candidate(int index, int centerX, int centerZ, int water, int mountain, int score, boolean pass) {}
}
