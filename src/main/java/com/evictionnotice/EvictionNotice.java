package com.evictionnotice;

import com.evictionnotice.command.EvictionCommand;
import com.evictionnotice.config.EvictionConfig;
import com.evictionnotice.duck.PoiManagerDimensionAccess;
import com.evictionnotice.eviction.EvictionService;
import com.evictionnotice.zone.ZoneManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvictionNotice implements ModInitializer {

    public static final String MOD_ID = "eviction_notice";
    public static final Logger LOGGER = LoggerFactory.getLogger("Eviction Notice");

    private static volatile ZoneManager zoneManager;
    private static volatile EvictionConfig config;

    public static ZoneManager zones() {
        return zoneManager;
    }

    public static EvictionConfig config() {
        return config;
    }

    @Override
    public void onInitialize() {
        config = EvictionConfig.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EvictionCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ZoneManager zm = ZoneManager.create(server);
            zm.load();
            zoneManager = zm;
            for (ServerLevel level : server.getAllLevels()) {
                ((PoiManagerDimensionAccess) level.getPoiManager())
                        .evictionNotice$setDimension(level.dimension());
            }
            EvictionService.releaseExistingClaims(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ZoneManager zm = zoneManager;
            if (zm != null) {
                zm.save();
            }
            zoneManager = null;
        });

        ServerLevelEvents.LOAD.register((server, level) ->
                ((PoiManagerDimensionAccess) level.getPoiManager())
                        .evictionNotice$setDimension(level.dimension()));

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Villager villager) {
                EvictionService.onVillagerLoad(level, villager);
            }
        });

        ServerTickEvents.END_LEVEL_TICK.register(EvictionService::onWorldTick);

        LOGGER.info("[Eviction Notice] initialized");
    }
}
