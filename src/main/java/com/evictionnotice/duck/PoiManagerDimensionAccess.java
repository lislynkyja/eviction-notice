package com.evictionnotice.duck;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Provides {@code PoiManager} with a dimension, since it doesn't have a way to "know" on its own. Set when the level loads,
 * implemented by {@code PoiManagerMixin}.
 */
public interface PoiManagerDimensionAccess {
    void evictionNotice$setDimension(ResourceKey<Level> dimension);

    ResourceKey<Level> evictionNotice$getDimension();
}
