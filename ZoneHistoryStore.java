package dev.oz.zaruba;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZoneHistoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private Data data;

    private ZoneHistoryStore(Path path, Data data) {
        this.path = path;
        this.data = data;
    }

    public static ZoneHistoryStore forWorld(ServerWorld world) {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("oz").resolve("worlds");
        Path path = dir.resolve("zones-" + Long.toUnsignedString(world.getSeed()) + ".json");
        try {
            Files.createDirectories(dir);
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    Data data = GSON.fromJson(reader, Data.class);
                    if (data != null) return new ZoneHistoryStore(path, data);
                }
            }
        } catch (IOException e) {
            OzMod.LOGGER.warn("Could not read OZ zone history {}", path, e);
        }
        return new ZoneHistoryStore(path, new Data());
    }

    public boolean hasOrigin() { return data.hasOrigin; }
    public int originX() { return data.originX; }
    public int originZ() { return data.originZ; }
    public int nextIndex() { return Math.max(1, data.nextIndex); }

    public void ensureOrigin(int x, int z) {
        if (!data.hasOrigin) {
            data.hasOrigin = true;
            data.originX = x;
            data.originZ = z;
            data.nextIndex = 1;
            save();
        }
    }

    public void resetOrigin(int x, int z) {
        data.hasOrigin = true;
        data.originX = x;
        data.originZ = z;
        data.nextIndex = 1;
        save();
    }

    public void advanceTo(int nextIndex) {
        data.nextIndex = Math.max(1, nextIndex);
    }

    public void flush() {
        save();
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            OzMod.LOGGER.warn("Could not save OZ zone history {}", path, e);
        }
    }

    private static final class Data {
        boolean hasOrigin = false;
        int originX = 0;
        int originZ = 0;
        int nextIndex = 1;
    }
}
