package dev.oz.zaruba;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OzConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ZoneSearch zoneSearch = new ZoneSearch();
    public Match match = new Match();
    public Pressure pressure = new Pressure();
    public FutureArenaGeneration futureArenaGeneration = new FutureArenaGeneration();

    public static OzConfig load() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("oz");
        Path path = dir.resolve("oz.json");
        try {
            Files.createDirectories(dir);
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    OzConfig cfg = GSON.fromJson(reader, OzConfig.class);
                    if (cfg != null) {
                        cfg.sanitize();
                        return cfg;
                    }
                }
            }
            OzConfig cfg = new OzConfig();
            cfg.sanitize();
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(cfg, writer);
            }
            return cfg;
        } catch (IOException e) {
            OzMod.LOGGER.error("Failed to load OZ config; using defaults", e);
            OzConfig cfg = new OzConfig();
            cfg.sanitize();
            return cfg;
        }
    }

    private void sanitize() {
        if (zoneSearch == null) zoneSearch = new ZoneSearch();
        if (match == null) match = new Match();
        if (pressure == null) pressure = new Pressure();
        if (futureArenaGeneration == null) futureArenaGeneration = new FutureArenaGeneration();

        zoneSearch.candidateSpacing = clamp(zoneSearch.candidateSpacing, 1024, 50000);
        zoneSearch.maxCandidates = clamp(zoneSearch.maxCandidates, 1, 256);
        zoneSearch.samplesPerAxis = clamp(zoneSearch.samplesPerAxis, 3, 9);
        if ((zoneSearch.samplesPerAxis & 1) == 0) zoneSearch.samplesPerAxis++;
        zoneSearch.candidatesPerTick = clamp(zoneSearch.candidatesPerTick, 1, 8);
        zoneSearch.waterThreshold = clamp(zoneSearch.waterThreshold, 0.0, 1.0);
        zoneSearch.mountainThreshold = clamp(zoneSearch.mountainThreshold, 0.0, 1.0);
        zoneSearch.mixedThreshold = clamp(zoneSearch.mixedThreshold, 0.0, 1.0);

        match.finalStartMinutes = clamp(match.finalStartMinutes, 5, 90);
        match.shrinkMinutes = clamp(match.shrinkMinutes, 1, 60);
        match.defaultArenaSize = clamp(match.defaultArenaSize, 128, 8192);
        match.defaultFinalSize = clamp(match.defaultFinalSize, 16, match.defaultArenaSize);
        match.centerPressureStartMinutes = clamp(match.centerPressureStartMinutes, 1, 120);
        match.centerPressureEndMinutes = Math.max(match.centerPressureStartMinutes + 1, match.centerPressureEndMinutes);
        match.glowStartMinutes = clamp(match.glowStartMinutes, 1, 120);
        match.glowEndMinutes = Math.max(match.glowStartMinutes + 1, match.glowEndMinutes);
        match.overtimeStartMinutes = clamp(match.overtimeStartMinutes, 1, 120);
        match.revealAllMinutes = Math.max(match.overtimeStartMinutes, match.revealAllMinutes);
        match.suddenDeathMinutes = Math.max(match.revealAllMinutes + 1, match.suddenDeathMinutes);
        match.overtimeBorderSize = clamp(match.overtimeBorderSize, 8, match.defaultArenaSize);
        match.suddenDeathBorderSize = clamp(match.suddenDeathBorderSize, 4, match.overtimeBorderSize);
        match.overtimeShrinkMinutes = clamp(match.overtimeShrinkMinutes, 1, 30);

        pressure.startMinute = clamp(pressure.startMinute, 1, 120);
        pressure.fullStrengthMinute = Math.max(pressure.startMinute + 1, pressure.fullStrengthMinute);
        pressure.skyClearanceStart = clamp(pressure.skyClearanceStart, 16, 128);
        pressure.skyClearanceEnd = clamp(pressure.skyClearanceEnd, 8, pressure.skyClearanceStart);
        pressure.depthClearanceStart = clamp(pressure.depthClearanceStart, 16, 128);
        pressure.depthClearanceEnd = clamp(pressure.depthClearanceEnd, 8, pressure.depthClearanceStart);
        pressure.warningSeconds = clamp(pressure.warningSeconds, 1, 30);
        pressure.damageAfterSeconds = Math.max(pressure.warningSeconds, clamp(pressure.damageAfterSeconds, 1, 60));
        pressure.damagePerSecond = clamp(pressure.damagePerSecond, 0.5, 10.0);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    public static final class ZoneSearch {
        public int candidateSpacing = 6500;
        public int maxCandidates = 24;
        public int samplesPerAxis = 5;
        public int candidatesPerTick = 1;
        public double waterThreshold = 0.30;
        public double mountainThreshold = 0.30;
        public double mixedThreshold = 0.20;
    }

    public static final class Match {
        public int finalStartMinutes = 18;
        public int shrinkMinutes = 24;
        public int defaultArenaSize = 1008;
        public int defaultFinalSize = 112;
        public int centerPressureStartMinutes = 18;
        public int centerPressureEndMinutes = 42;
        public int glowStartMinutes = 30;
        public int glowEndMinutes = 42;
        public int overtimeStartMinutes = 42;
        public int revealAllMinutes = 45;
        public int suddenDeathMinutes = 48;
        public int overtimeBorderSize = 48;
        public int suddenDeathBorderSize = 24;
        public int overtimeShrinkMinutes = 4;
    }

    public static final class Pressure {
        public boolean enabled = true;
        public int startMinute = 18;
        public int fullStrengthMinute = 42;
        public int skyClearanceStart = 64;
        public int skyClearanceEnd = 20;
        public int depthClearanceStart = 64;
        public int depthClearanceEnd = 24;
        public int warningSeconds = 3;
        public int damageAfterSeconds = 6;
        public double damagePerSecond = 2.0;
    }

    public static final class FutureArenaGeneration {
        public boolean presetZonesEnabled = false;
        public boolean structureRulesEnabled = false;
        public String note = "Reserved for template-zone generation and registry-based vanilla/modded structure requirements.";
    }
}
