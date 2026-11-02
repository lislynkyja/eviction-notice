package com.evictionnotice.eviction;

import com.evictionnotice.EvictionNotice;
import com.evictionnotice.config.EvictionConfig;
import com.evictionnotice.zone.Zone;
import com.evictionnotice.zone.ZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Responsible for moving mobs out of zones and releasing beds, workstations and meeting points that villagers
 * claimed inside a zone. It also holds the sweep logic, which runs every {@code evictionIntervalTicks} and only looks at
 * mobs near a zone.
 */
public final class EvictionService {

    private static final List<MemoryModuleType<GlobalPos>> CLAIM_MEMORIES = List.of(
            MemoryModuleType.HOME,
            MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE,
            MemoryModuleType.MEETING_POINT
    );

    private static final Direction[] EXIT_DIRECTIONS = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
    private static final int SAFE_SEARCH_COLUMNS = 16;
    private static final int SAFE_SEARCH_HEIGHT = 16;

    private EvictionService() {
    }

    public static void onWorldTick(ServerLevel level) {
        EvictionConfig cfg = EvictionNotice.config();
        ZoneManager zm = EvictionNotice.zones();
        if (cfg == null || zm == null) {
            return;
        }
        int interval = Math.max(1, cfg.evictionIntervalTicks);
        if (level.getGameTime() % interval != 0L) {
            return;
        }
        List<Zone> zones = zm.zonesIn(level.dimension());
        if (zones.isEmpty()) {
            return;
        }

        for (Zone zone : zones) {
            AABB box = new AABB(
                    zone.centerX() - zone.radius(), zone.yMin(), zone.centerZ() - zone.radius(),
                    zone.centerX() + zone.radius() + 1.0, zone.yMax() + 1.0, zone.centerZ() + zone.radius() + 1.0);

            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), box,
                    e -> EvictionSubjects.isSubject(e, cfg))) {
                if (mob instanceof Villager villager) {
                    releaseZonedClaims(level, villager, zm);
                }
                if (zone.contains(mob.blockPosition())) {
                    evict(level, mob, zone, zones);
                }
            }
        }
    }

    /** Runs when a zone is created and on server start, for every loaded villager. */
    public static void releaseExistingClaims(MinecraftServer server) {
        ZoneManager zm = EvictionNotice.zones();
        if (zm == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!zm.hasZones(level.dimension())) {
                continue;
            }
            for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class),
                    e -> true)) {
                releaseZonedClaims(level, villager, zm);
            }
        }
    }

    /**
     * Villagers in unloaded chunks when a zone was created can't walk into it, so the sweep
     * never reaches them. This releases their claims when they load.
     */
    public static void onVillagerLoad(ServerLevel level, Villager villager) {
        ZoneManager zm = EvictionNotice.zones();
        if (zm == null) {
            return;
        }
        releaseZonedClaims(level, villager, zm);
    }

    private static void releaseZonedClaims(ServerLevel level, Villager villager, ZoneManager zm) {
        for (MemoryModuleType<GlobalPos> memory : CLAIM_MEMORIES) {
            villager.getBrain().getMemory(memory).ifPresent(claim -> {
                if (zm.isInAnyZone(claim.dimension(), claim.pos())) {
                    ServerLevel target = level.getServer().getLevel(claim.dimension());
                    if (target != null) {
                        PoiManager poi = target.getPoiManager();
                        try {
                            if (poi.exists(claim.pos(), holder -> true)) {
                                poi.release(claim.pos());
                            }
                        } catch (Exception ignored) {
                            // release() can throw if the section isn't loaded.
                        }
                    }
                    villager.getBrain().eraseMemory(memory);
                }
            });
        }
    }

    private static void evict(ServerLevel level, Mob mob, Zone zone, List<Zone> zones) {
        BlockPos from = mob.blockPosition();
        // A mob under a roof, in a cave or in the Nether is placed near its own height, not on top of
        // the column.
        boolean onSurface = from.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX(), from.getZ());
        BlockPos best = null;
        for (Direction dir : EXIT_DIRECTIONS) {
            BlockPos exit = exitTowards(level, mob, zones, zone, from, dir, onSurface);
            if (exit != null && (best == null || horizontalDistance(from, exit) < horizontalDistance(from, best))) {
                best = exit;
            }
        }
        if (best == null) {
            return;
        }
        mob.snapTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, mob.getYRot(), mob.getXRot());
        mob.getNavigation().stop();
    }

    /**
     * Goes past the zone's edge in one direction and past every further zone it lands in, then on
     * until a column has a safe spot. Gives up at an unloaded chunk.
     */
    private static BlockPos exitTowards(ServerLevel level, Mob mob, List<Zone> zones, Zone zone, BlockPos from,
                                        Direction dir, boolean onSurface) {
        int x = from.getX();
        int z = from.getZ();
        Zone current = zone;
        // At most one jump per zone: going in one direction, the search never returns to a zone it
        // has already passed.
        int jumps = 0;
        int unsafeColumns = 0;
        while (true) {
            if (current != null) {
                if (jumps++ >= zones.size()) {
                    return null;
                }
                switch (dir) {
                    case WEST -> x = current.centerX() - current.radius() - 1;
                    case EAST -> x = current.centerX() + current.radius() + 1;
                    case NORTH -> z = current.centerZ() - current.radius() - 1;
                    default -> z = current.centerZ() + current.radius() + 1;
                }
            } else {
                if (unsafeColumns++ >= SAFE_SEARCH_COLUMNS) {
                    return null;
                }
                x += dir.getStepX();
                z += dir.getStepZ();
            }
            // Level.getHeight gives the bottom of the world for unloaded chunks.
            if (!loadedAround(level, x, z)) {
                return null;
            }
            BlockPos spot = safeSpot(level, mob, x, z, from.getY(), onSurface);
            if (spot == null) {
                current = null;
                continue;
            }
            current = zoneAt(zones, spot);
            if (current == null) {
                return spot;
            }
        }
    }

    private static BlockPos safeSpot(ServerLevel level, Mob mob, int x, int z, int nearY, boolean onSurface) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (onSurface) {
            return isSafe(level, mob, x, top, z) ? new BlockPos(x, top, z) : null;
        }
        for (int d = 0; d <= SAFE_SEARCH_HEIGHT; d++) {
            for (int y : d == 0 ? new int[]{nearY} : new int[]{nearY + d, nearY - d}) {
                if (y >= level.getMinY() && y <= level.getMaxY() && isSafe(level, mob, x, y, z)) {
                    return new BlockPos(x, y, z);
                }
            }
        }
        // Nothing near the mob's height, for example in solid rock. The surface is the old fallback,
        // but in the Nether that's the bedrock ceiling.
        if (!level.dimensionType().hasCeiling() && isSafe(level, mob, x, top, z)) {
            return new BlockPos(x, top, z);
        }
        return null;
    }

    /**
     * The same checks vanilla makes before a pet teleports to its owner
     * ({@code TamableAnimal#canTeleportTo}).
     */
    private static boolean isSafe(ServerLevel level, Mob mob, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (WalkNodeEvaluator.getPathTypeStatic(mob, pos) != PathType.WALKABLE) {
            return false;
        }
        if (level.getBlockState(pos.below()).getBlock() instanceof LeavesBlock) {
            return false;
        }
        AABB box = mob.getBoundingBox().move(x + 0.5 - mob.getX(), y - mob.getY(), z + 0.5 - mob.getZ());
        return level.noCollision(mob, box);
    }

    // The safety checks also look at the neighbouring blocks, which can be in the next chunk.
    private static boolean loadedAround(ServerLevel level, int x, int z) {
        int y = level.getMinY();
        return level.isLoaded(new BlockPos(x - 1, y, z - 1)) && level.isLoaded(new BlockPos(x + 1, y, z - 1))
                && level.isLoaded(new BlockPos(x - 1, y, z + 1)) && level.isLoaded(new BlockPos(x + 1, y, z + 1));
    }

    private static Zone zoneAt(List<Zone> zones, BlockPos pos) {
        for (Zone zone : zones) {
            if (zone.contains(pos)) {
                return zone;
            }
        }
        return null;
    }

    private static int horizontalDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }
}
