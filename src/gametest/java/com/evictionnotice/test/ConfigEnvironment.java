package com.evictionnotice.test;

import com.evictionnotice.EvictionNotice;
import com.evictionnotice.config.EvictionConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;

/**
 * Test environment that changes the trader options for one batch of tests and puts the old
 * values back afterward. Tests in a batch run at the same time and share the config so the
 * options can't be changed by a single test.
 */
public record ConfigEnvironment(boolean affectWanderingTraders, boolean affectTraderLlamas)
        implements TestEnvironmentDefinition<ConfigEnvironment> {

    public static final MapCodec<ConfigEnvironment> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("affect_wandering_traders").forGetter(ConfigEnvironment::affectWanderingTraders),
            Codec.BOOL.optionalFieldOf("affect_trader_llamas", true).forGetter(ConfigEnvironment::affectTraderLlamas)
    ).apply(instance, ConfigEnvironment::new));

    @Override
    public ConfigEnvironment setup(ServerLevel level) {
        EvictionConfig.WanderingTraders traders = EvictionNotice.config().mobs.wanderingTraders;
        ConfigEnvironment previous = new ConfigEnvironment(traders.enabled, traders.llamas);
        traders.enabled = affectWanderingTraders;
        traders.llamas = affectTraderLlamas;
        return previous;
    }

    @Override
    public void teardown(ServerLevel level, ConfigEnvironment previous) {
        EvictionConfig.WanderingTraders traders = EvictionNotice.config().mobs.wanderingTraders;
        traders.enabled = previous.affectWanderingTraders();
        traders.llamas = previous.affectTraderLlamas();
    }

    @Override
    public MapCodec<ConfigEnvironment> codec() {
        return CODEC;
    }
}
