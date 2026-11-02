package com.evictionnotice.mixin;

import com.evictionnotice.EvictionNotice;
import com.evictionnotice.config.EvictionConfig;
import com.evictionnotice.eviction.EvictionSubjects;
import com.evictionnotice.zone.ZoneManager;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Marks zones as blocked when villagers plan a path so they walk around them. A villager that
 * is already inside gets moved out by {@code EvictionService}.
 */
@Mixin(WalkNodeEvaluator.class)
public class WalkNodeEvaluatorMixin {

    @ModifyReturnValue(
            method = "getPathTypeOfMob(Lnet/minecraft/world/level/pathfinder/PathfindingContext;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("RETURN"))
    private PathType evictionNotice$blockZonedNodes(PathType original,
                                                    PathfindingContext context, int x, int y, int z, Mob mob) {
        if (original == PathType.BLOCKED) {
            return original;
        }
        ZoneManager zones = EvictionNotice.zones();
        EvictionConfig cfg = EvictionNotice.config();
        if (zones == null || cfg == null) {
            return original;
        }
        if (!EvictionSubjects.isSubject(mob, cfg)) {
            return original;
        }
        Level level = mob.level();
        if (zones.isInAnyZone(level.dimension(), x, y, z)) {
            return PathType.BLOCKED;
        }
        return original;
    }
}
