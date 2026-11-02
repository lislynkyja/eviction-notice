package com.evictionnotice.zone;

import com.evictionnotice.EvictionNotice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All zones are saved to {@code eviction_notice/zones.json} in the world folder.
 *
 * <p>Every change creates a new immutable snapshot instead of editing the current one. Vanilla
 * pathfinding runs on the server thread, but a mod that moves it to other threads could read
 * the zones while they're being edited and crash. A lookup first checks if the chunk has a zone in
 * it to prevent looping over every zone for each villager path node.
 */
public final class ZoneManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Path file;

    private final List<Zone> zones = new ArrayList<>();

    private volatile Map<String, DimensionZones> snapshot = Map.of();

    private ZoneManager(MinecraftServer server, Path file) {
        this.server = server;
        this.file = file;
    }

    public static ZoneManager create(MinecraftServer server) {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("eviction_notice");
        return new ZoneManager(server, dir.resolve("zones.json"));
    }

    public MinecraftServer server() {
        return server;
    }

    private record DimensionZones(List<Zone> zones, LongSet chunks) {
        boolean maybeContains(int x, int z) {
            return chunks.contains(ChunkPos.pack(x >> 4, z >> 4));
        }
    }

    public boolean isInAnyZone(ResourceKey<Level> dimension, int x, int y, int z) {
        DimensionZones dz = snapshot.get(dimension.identifier().toString());
        if (dz == null || !dz.maybeContains(x, z)) {
            return false;
        }
        for (Zone zone : dz.zones()) {
            if (zone.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInAnyZone(ResourceKey<Level> dimension, BlockPos pos) {
        return isInAnyZone(dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean hasZones(ResourceKey<Level> dimension) {
        DimensionZones dz = snapshot.get(dimension.identifier().toString());
        return dz != null && !dz.zones().isEmpty();
    }

    public List<Zone> zonesIn(ResourceKey<Level> dimension) {
        DimensionZones dz = snapshot.get(dimension.identifier().toString());
        return dz == null ? List.of() : dz.zones();
    }

    public List<String> zoneNamesContaining(ResourceKey<Level> dimension, BlockPos pos) {
        List<String> names = new ArrayList<>();
        for (Zone zone : zonesIn(dimension)) {
            if (zone.contains(pos)) {
                names.add(zone.name());
            }
        }
        return names;
    }

    /** Returns false if the owner already has a zone with this name, in any case. */
    public synchronized boolean addZone(Zone zone) {
        boolean taken = zones.stream()
                .anyMatch(z -> z.owner().equals(zone.owner()) && z.name().equalsIgnoreCase(zone.name()));
        if (taken) {
            return false;
        }
        zones.add(zone);
        rebuildSnapshot();
        save();
        return true;
    }

    public synchronized boolean removeZone(UUID owner, String name) {
        boolean removed = zones.removeIf(z -> z.owner().equals(owner) && z.name().equalsIgnoreCase(name));
        if (removed) {
            rebuildSnapshot();
            save();
        }
        return removed;
    }

    public synchronized List<Zone> listZones(UUID owner) {
        List<Zone> out = new ArrayList<>();
        for (Zone z : zones) {
            if (z.owner().equals(owner)) {
                out.add(z);
            }
        }
        return out;
    }

    private void rebuildSnapshot() {
        Map<String, List<Zone>> byDim = new HashMap<>();
        for (Zone z : zones) {
            byDim.computeIfAbsent(z.dimensionId(), k -> new ArrayList<>()).add(z);
        }
        Map<String, DimensionZones> next = new HashMap<>();
        for (Map.Entry<String, List<Zone>> e : byDim.entrySet()) {
            LongSet chunks = new LongOpenHashSet();
            for (Zone z : e.getValue()) {
                int minCX = (z.centerX() - z.radius()) >> 4;
                int maxCX = (z.centerX() + z.radius()) >> 4;
                int minCZ = (z.centerZ() - z.radius()) >> 4;
                int maxCZ = (z.centerZ() + z.radius()) >> 4;
                for (int cx = minCX; cx <= maxCX; cx++) {
                    for (int cz = minCZ; cz <= maxCZ; cz++) {
                        chunks.add(ChunkPos.pack(cx, cz));
                    }
                }
            }
            next.put(e.getKey(), new DimensionZones(
                    Collections.unmodifiableList(new ArrayList<>(e.getValue())), chunks));
        }
        this.snapshot = Map.copyOf(next);
    }

    public synchronized void load() {
        zones.clear();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    JsonArray arr = root.getAsJsonArray("zones");
                    if (arr != null) {
                        for (var el : arr) {
                            JsonObject o = el.getAsJsonObject();
                            zones.add(new Zone(
                                    UUID.fromString(o.get("owner").getAsString()),
                                    o.get("name").getAsString(),
                                    o.get("dimension").getAsString(),
                                    o.get("x").getAsInt(),
                                    o.get("y").getAsInt(),
                                    o.get("z").getAsInt(),
                                    o.get("radius").getAsInt(),
                                    o.get("yMin").getAsInt(),
                                    o.get("yMax").getAsInt()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            EvictionNotice.LOGGER.error("[Eviction Notice] failed to load zones from {}", file, e);
        }
        rebuildSnapshot();
        EvictionNotice.LOGGER.info("[Eviction Notice] loaded {} zone(s)", zones.size());
    }

    public synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Zone z : zones) {
            JsonObject o = new JsonObject();
            o.addProperty("owner", z.owner().toString());
            o.addProperty("name", z.name());
            o.addProperty("dimension", z.dimensionId());
            o.addProperty("x", z.centerX());
            o.addProperty("y", z.centerY());
            o.addProperty("z", z.centerZ());
            o.addProperty("radius", z.radius());
            o.addProperty("yMin", z.yMin());
            o.addProperty("yMax", z.yMax());
            arr.add(o);
        }
        root.add("zones", arr);
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            EvictionNotice.LOGGER.error("[Eviction Notice] failed to save zones to {}", file, e);
        }
    }
}
