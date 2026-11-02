package com.evictionnotice.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class EvictionTestMod implements ModInitializer {

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.TEST_ENVIRONMENT_DEFINITION_TYPE,
                Identifier.fromNamespaceAndPath("eviction_notice_test", "config"), ConfigEnvironment.CODEC);
    }
}
