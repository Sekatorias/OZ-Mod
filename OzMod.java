package dev.oz.zaruba;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OzMod implements ModInitializer {
    public static final String MOD_ID = "oz";
    public static final Logger LOGGER = LoggerFactory.getLogger("OZ");

    @Override
    public void onInitialize() {
        OzConfig config = OzConfig.load();
        ZoneSearchManager zones = new ZoneSearchManager(config);
        BorderBridge border = new BorderBridge(config);
        ArenaPressureManager pressure = new ArenaPressureManager(config);
        MatchPacingManager pacing = new MatchPacingManager(config);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                OzCommands.register(dispatcher, zones, border, config));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            zones.tick(server);
            pressure.tick(server);
            pacing.tick(server);
        });

        LOGGER.info("OZ 0.1.1 initialized: fast zone search, direct border math, arena pressure");
    }
}
