package com.evictionnotice.zone;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * A player's zone, defined by {@code radius} blocks in each direction from the center and from
 * {@code yMin} to {@code yMax}. A zone is immutable after creation, because it's part of the
 * {@link ZoneManager} snapshot.
 */
public record Zone(UUID owner, String name, String dimensionId,
                   int centerX, int centerY, int centerZ,
                   int radius, int yMin, int yMax) {

    public boolean containsColumn(int x, int z) {
        return Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius;
    }

    public boolean contains(int x, int y, int z) {
        return y >= yMin && y <= yMax && containsColumn(x, z);
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }
}
