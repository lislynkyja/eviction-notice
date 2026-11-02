package com.evictionnotice.test;

import com.evictionnotice.EvictionNotice;
import com.evictionnotice.eviction.EvictionService;
import com.evictionnotice.zone.Zone;
import com.evictionnotice.zone.ZoneManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.List;
import java.util.UUID;

/**
 * Each test runs in its own 8x8 box with a stone floor and a zone of radius 1 in the middle
 * (x and z 3 to 5). Mobs are spawned without AI so they don't wander off on their own.
 */
public class EvictionGameTests {

    private static final BlockPos ZONE_CENTER = new BlockPos(4, 1, 4);
    private static final int SWEEP_TICKS = 40;
    private static final String TRADERS = "eviction_notice_test:traders";
    private static final String TRADERS_WITHOUT_LLAMAS = "eviction_notice_test:traders_without_llamas";
    private static final String BIG_ZONE = "eviction_notice_test:big_zone";

    @GameTest
    public void villagerPathsAroundZone(GameTestHelper helper) {
        assertPathsAroundZone(helper, EntityType.VILLAGER);
    }

    @GameTest
    public void bedInsideZoneIsHidden(GameTestHelper helper) {
        floor(helper);
        zone(helper);
        BlockPos inside = bed(helper, new BlockPos(3, 1, 4));
        BlockPos outside = bed(helper, new BlockPos(0, 1, 1));
        PoiManager poi = helper.getLevel().getPoiManager();

        // Placing a bed doesn't add its POI right away, the server adds it a bit later.
        helper.succeedWhen(() -> {
            helper.assertTrue(poi.existsAtPosition(PoiTypes.HOME, inside), "The bed inside isn't a POI yet");
            List<BlockPos> found = poi.getInRange(type -> type.is(PoiTypes.HOME),
                    helper.absolutePos(ZONE_CENTER), 8, PoiManager.Occupancy.ANY).map(PoiRecord::getPos).toList();
            helper.assertTrue(found.contains(outside), "The bed outside wasn't found");
            helper.assertFalse(found.contains(inside), "The bed inside the zone was found");
        });
    }

    @GameTest(maxTicks = SWEEP_TICKS)
    public void sweepReleasesClaimAndMovesVillagerOut(GameTestHelper helper) {
        floor(helper);
        Zone zone = zone(helper);
        BlockPos bed = bed(helper, new BlockPos(3, 1, 4));
        Villager villager = spawnStill(helper, EntityType.VILLAGER, new BlockPos(5, 1, 5));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), bed));

        helper.succeedWhen(() -> {
            helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "The villager still has the bed");
            helper.assertFalse(zone.contains(villager.blockPosition()), "The villager is still in the zone");
        });
    }

    @GameTest
    public void claimReleasedWhenVillagerLoads(GameTestHelper helper) {
        floor(helper);
        zone(helper);
        BlockPos bed = bed(helper, new BlockPos(3, 1, 4));
        Villager villager = EntityType.VILLAGER.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        villager.setNoAi(true);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 6));
        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), bed));

        helper.getLevel().addFreshEntity(villager);
        helper.succeedWhen(() ->
                helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "The villager still has the bed"));
    }

    /**
     * releaseExistingClaims goes through every loaded villager, including the ones in other tests.
     * It runs in its own batch so it can't release the claim the sweep test is waiting for.
     */
    @GameTest(environment = "eviction_notice_test:separate_batch")
    public void releaseExistingClaimsClearsLoadedVillagers(GameTestHelper helper) {
        floor(helper);
        zone(helper);
        BlockPos bed = bed(helper, new BlockPos(3, 1, 4));
        Villager villager = spawnStill(helper, EntityType.VILLAGER, new BlockPos(1, 1, 6));
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), bed));

        // Runs on server start and when a zone is created; a test can't restart the server.
        EvictionService.releaseExistingClaims(helper.getLevel().getServer());
        helper.assertFalse(villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "The villager still has the bed");
        helper.succeed();
    }

    @GameTest(maxTicks = SWEEP_TICKS, skyAccess = true)
    public void villagerIsMovedOut(GameTestHelper helper) {
        floor(helper);
        Zone zone = zone(helper);
        Villager villager = spawnStill(helper, EntityType.VILLAGER, ZONE_CENTER);
        int floorY = helper.absolutePos(ZONE_CENTER).getY();

        helper.succeedWhen(() -> {
            helper.assertFalse(zone.contains(villager.blockPosition()), "The villager is still in the zone");
            helper.assertValueEqual(floorY, villager.blockPosition().getY(), "The villager's height after moving out");
        });
    }

    /**
     * Zone a covers x 4 to 6 and zone b x 0 to 4. Leaving zone a by its nearest edge used to put
     * the villager into zone b, and leaving zone b by its nearest edge put it back into zone a on
     * every sweep. This would trap the villager in an infinite loop of being bounced between zones.
     */
    @GameTest(maxTicks = SWEEP_TICKS, skyAccess = true)
    public void villagerIsMovedOutOfOverlappingZones(GameTestHelper helper) {
        floor(helper);
        Zone a = zone(helper, new BlockPos(5, 1, 4), 1);
        Zone b = zone(helper, new BlockPos(2, 1, 4), 2);
        Villager villager = spawnStill(helper, EntityType.VILLAGER, new BlockPos(4, 1, 4));

        helper.succeedWhen(() -> {
            helper.assertFalse(a.contains(villager.blockPosition()), "The villager is still in zone a");
            helper.assertFalse(b.contains(villager.blockPosition()), "The villager is still in zone b");
        });
    }

    /**
     * All four edges are equally close, so the villager would leave to the west, onto lava.
     * Walls keep the lava from flowing into the zone.
     */
    @GameTest(maxTicks = SWEEP_TICKS, skyAccess = true)
    public void villagerIsNotMovedIntoLava(GameTestHelper helper) {
        floor(helper);
        for (int z = 0; z < 8; z++) {
            boolean end = z == 0 || z == 7;
            helper.setBlock(1, 1, z, Blocks.STONE);
            helper.setBlock(2, 1, z, end ? Blocks.STONE : Blocks.LAVA);
            helper.setBlock(3, 1, z, Blocks.STONE);
        }
        Zone zone = zone(helper);
        Villager villager = spawnStill(helper, EntityType.VILLAGER, ZONE_CENTER);

        helper.succeedWhen(() -> {
            BlockPos pos = villager.blockPosition();
            helper.assertFalse(zone.contains(pos), "The villager is still in the zone");
            helper.assertFalse(helper.getLevel().getBlockState(pos).is(Blocks.LAVA)
                    || helper.getLevel().getBlockState(pos.below()).is(Blocks.LAVA), "The villager was moved onto the lava");
        });
    }

    /** The villager has to stay at floor height, not land on the roof. */
    @GameTest(maxTicks = SWEEP_TICKS, skyAccess = true)
    public void villagerUnderARoofStaysAtItsHeight(GameTestHelper helper) {
        floor(helper);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(x, 3, z, Blocks.STONE);
            }
        }
        Zone zone = zone(helper);
        Villager villager = spawnStill(helper, EntityType.VILLAGER, ZONE_CENTER);
        int floorY = helper.absolutePos(ZONE_CENTER).getY();

        helper.succeedWhen(() -> {
            helper.assertFalse(zone.contains(villager.blockPosition()), "The villager is still in the zone");
            helper.assertValueEqual(floorY, villager.blockPosition().getY(), "The villager's height after moving out");
        });
    }

    /**
     * The zone is so big that all its edges are in unloaded chunks, where a villager used to end up
     * at the bottom of the world. It runs alone because the zone covers the other tests.
     */
    @GameTest(environment = BIG_ZONE, maxTicks = 100, skyAccess = true)
    public void villagerIsNotMovedIntoUnloadedChunks(GameTestHelper helper) {
        floor(helper);
        Zone zone = zone(helper, ZONE_CENTER, 200);
        Villager villager = spawnStill(helper, EntityType.VILLAGER, ZONE_CENTER);
        BlockPos center = helper.absolutePos(ZONE_CENTER);
        int floorY = center.getY();

        helper.runAfterDelay(SWEEP_TICKS + 5, () -> {
            try {
                helper.assertFalse(helper.getLevel().isLoaded(center.west(201)),
                        "The chunk past the west edge is loaded, so the test proves nothing");
                helper.assertValueEqual(floorY, villager.blockPosition().getY(), "The villager's height");
                helper.assertTrue(zone.contains(villager.blockPosition()), "The villager was moved out");
            } finally {
                EvictionNotice.zones().removeZone(zone.owner(), zone.name());
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = SWEEP_TICKS, skyAccess = true)
    public void zombieVillagerIsMovedOut(GameTestHelper helper) {
        assertMovedOut(helper, EntityType.ZOMBIE_VILLAGER);
    }

    @GameTest(maxTicks = SWEEP_TICKS, skyAccess = true)
    public void ironGolemStaysByDefault(GameTestHelper helper) {
        assertStaysInZone(helper, EntityType.IRON_GOLEM);
    }

    @GameTest(maxTicks = SWEEP_TICKS, skyAccess = true)
    public void wanderingTraderStaysByDefault(GameTestHelper helper) {
        assertStaysInZone(helper, EntityType.WANDERING_TRADER);
    }

    @GameTest(maxTicks = SWEEP_TICKS, skyAccess = true)
    public void traderLlamaStaysByDefault(GameTestHelper helper) {
        assertStaysInZone(helper, EntityType.TRADER_LLAMA);
    }

    @GameTest(environment = TRADERS)
    public void wanderingTraderPathsAroundZoneWhenEnabled(GameTestHelper helper) {
        assertPathsAroundZone(helper, EntityType.WANDERING_TRADER);
    }

    @GameTest(environment = TRADERS)
    public void traderLlamaPathsAroundZoneWhenEnabled(GameTestHelper helper) {
        assertPathsAroundZone(helper, EntityType.TRADER_LLAMA);
    }

    @GameTest(environment = TRADERS, maxTicks = SWEEP_TICKS, skyAccess = true)
    public void wanderingTraderIsMovedOutWhenEnabled(GameTestHelper helper) {
        assertMovedOut(helper, EntityType.WANDERING_TRADER);
    }

    @GameTest(environment = TRADERS, maxTicks = SWEEP_TICKS, skyAccess = true)
    public void traderLlamaIsMovedOutWhenEnabled(GameTestHelper helper) {
        assertMovedOut(helper, EntityType.TRADER_LLAMA);
    }

    @GameTest(environment = TRADERS_WITHOUT_LLAMAS, maxTicks = SWEEP_TICKS, skyAccess = true)
    public void traderLlamaStaysWhenLlamasAreOff(GameTestHelper helper) {
        assertStaysInZone(helper, EntityType.TRADER_LLAMA);
    }

    @GameTest
    public void zoneNameMustBeUnique(GameTestHelper helper) {
        ZoneManager zones = EvictionNotice.zones();
        UUID owner = UUID.randomUUID();
        String dimension = helper.getLevel().dimension().identifier().toString();
        Zone first = new Zone(owner, "Home", dimension, 0, 0, 0, 5, 0, 10);
        Zone second = new Zone(owner, "home", dimension, 100, 0, 100, 5, 0, 10);

        helper.assertTrue(zones.addZone(first), "The first zone wasn't added");
        helper.assertFalse(zones.addZone(second), "A second zone with the same name was added");
        helper.assertValueEqual(List.of(first), zones.listZones(owner), "The owner's zones");
        zones.removeZone(owner, "Home");
        helper.succeed();
    }

    private static void assertPathsAroundZone(GameTestHelper helper, EntityType<? extends Mob> type) {
        floor(helper);
        Mob mob = spawnStill(helper, type, new BlockPos(1, 1, 4));
        BlockPos target = helper.absolutePos(new BlockPos(7, 1, 4));
        // Navigation only plans paths for mobs on the ground, and a new mob isn't marked yet.
        mob.setOnGround(true);

        // Without a zone the straight line through the middle is the shortest path.
        Path before = mob.getNavigation().createPath(target, 0);
        helper.assertTrue(before != null && before.canReach(), "No path before the zone was added");
        Zone zone = zone(helper);
        helper.assertTrue(crosses(before, zone), "The path without a zone didn't cross the zone area");

        Path after = mob.getNavigation().createPath(target, 0);
        helper.assertTrue(after != null && after.canReach(), "No path around the zone");
        helper.assertFalse(crosses(after, zone), "The path goes through the zone");
        helper.succeed();
    }

    private static void assertMovedOut(GameTestHelper helper, EntityType<? extends Mob> type) {
        floor(helper);
        Zone zone = zone(helper);
        Mob mob = spawnStill(helper, type, ZONE_CENTER);

        helper.succeedWhen(() -> helper.assertFalse(zone.contains(mob.blockPosition()), "The mob is still in the zone"));
    }

    private static void assertStaysInZone(GameTestHelper helper, EntityType<? extends Mob> type) {
        floor(helper);
        Zone zone = zone(helper);
        Mob mob = spawnStill(helper, type, ZONE_CENTER);

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(zone.contains(mob.blockPosition()), "The mob was moved out");
            helper.succeed();
        });
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    /** Adds the zone around {@link #ZONE_CENTER}, owned by a new UUID so tests don't share zones. */
    private static Zone zone(GameTestHelper helper) {
        return zone(helper, ZONE_CENTER, 1);
    }

    private static Zone zone(GameTestHelper helper, BlockPos relativeCenter, int radius) {
        BlockPos center = helper.absolutePos(relativeCenter);
        BlockPos bottom = helper.absolutePos(BlockPos.ZERO);
        Zone zone = new Zone(UUID.randomUUID(), "test", helper.getLevel().dimension().identifier().toString(),
                center.getX(), center.getY(), center.getZ(), radius, bottom.getY(), bottom.getY() + 7);
        helper.assertTrue(EvictionNotice.zones().addZone(zone), "The zone wasn't added");
        return zone;
    }

    /** Places a bed with its foot at {@code foot} and its head one block east. Returns the head. */
    private static BlockPos bed(GameTestHelper helper, BlockPos foot) {
        BlockState state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        helper.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(foot.east(), state.setValue(BedBlock.PART, BedPart.HEAD));
        return helper.absolutePos(foot.east());
    }

    private static <T extends Mob> T spawnStill(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        T mob = helper.spawn(type, pos);
        mob.setNoAi(true);
        return mob;
    }

    private static boolean crosses(Path path, Zone zone) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (zone.contains(node.x, node.y, node.z)) {
                return true;
            }
        }
        return false;
    }
}
