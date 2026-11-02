package com.evictionnotice.mixin;

import com.evictionnotice.EvictionNotice;
import com.evictionnotice.duck.PoiManagerDimensionAccess;
import com.evictionnotice.zone.ZoneManager;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

/**
 * Hides beds and workstations inside zones, so villagers can't find or claim them. All POI
 * searches go through {@code getInRange}, {@code getInSquare} or {@code getInChunk}. Claims to POIs made
 * before a zone existed are released by {@code EvictionService} so villagers don't try to pathfind to them.
 */
@Mixin(PoiManager.class)
public abstract class PoiManagerMixin implements PoiManagerDimensionAccess {

    @Unique
    private ResourceKey<Level> evictionNotice$dimension;

    @Override
    public void evictionNotice$setDimension(ResourceKey<Level> dimension) {
        this.evictionNotice$dimension = dimension;
    }

    @Override
    public ResourceKey<Level> evictionNotice$getDimension() {
        return this.evictionNotice$dimension;
    }

    @ModifyReturnValue(
            method = "getInRange(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;",
            at = @At("RETURN"), require = 0)
    private Stream<PoiRecord> evictionNotice$filterInRange(Stream<PoiRecord> original) {
        return evictionNotice$filterZoned(original);
    }

    @ModifyReturnValue(
            method = "getInSquare(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;",
            at = @At("RETURN"), require = 0)
    private Stream<PoiRecord> evictionNotice$filterInSquare(Stream<PoiRecord> original) {
        return evictionNotice$filterZoned(original);
    }

    @ModifyReturnValue(
            method = "getInChunk(Ljava/util/function/Predicate;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;",
            at = @At("RETURN"), require = 0)
    private Stream<PoiRecord> evictionNotice$filterInChunk(Stream<PoiRecord> original) {
        return evictionNotice$filterZoned(original);
    }

    @Unique
    private Stream<PoiRecord> evictionNotice$filterZoned(Stream<PoiRecord> original) {
        ResourceKey<Level> dimension = this.evictionNotice$dimension;
        ZoneManager zones = EvictionNotice.zones();
        if (dimension == null || zones == null || !zones.hasZones(dimension)) {
            return original;
        }
        return original.filter(record -> !zones.isInAnyZone(dimension, record.getPos()));
    }
}
