package com.evictionnotice.config;

import com.evictionnotice.EvictionNotice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod configuration, stored as {@code config/eviction_notice.json}.
 */
public class EvictionConfig {

    public static final int CURRENT_VERSION = 2;

    public int configVersion = CURRENT_VERSION;
    public int evictionIntervalTicks = 20;
    public Mobs mobs = new Mobs();

    public static class Mobs {
        public boolean zombieVillagers = true;
        public boolean ironGolems = false;
        public WanderingTraders wanderingTraders = new WanderingTraders();
    }

    public static class WanderingTraders {
        public boolean enabled = false;
        // Only used while enabled is on.
        public boolean llamas = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "eviction_notice.json";

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static EvictionConfig load() {
        return load(path());
    }

    public static EvictionConfig load(Path p) {
        if (Files.exists(p)) {
            EvictionConfig cfg;
            int version;
            try (Reader r = Files.newBufferedReader(p)) {
                JsonElement json = JsonParser.parseReader(r);
                if (!json.isJsonObject()) {
                    return writeDefaults(p);
                }
                JsonObject obj = json.getAsJsonObject();
                version = obj.has("configVersion") ? obj.get("configVersion").getAsInt() : 1;
                if (version == 1) {
                    migrateFromVersion1(obj);
                }
                cfg = GSON.fromJson(obj, EvictionConfig.class);
            } catch (Exception e) {
                // The file stays as it is, so a typo doesn't cost the user their settings.
                EvictionNotice.LOGGER.warn("[Eviction Notice] failed to read config, using defaults until it's fixed", e);
                return new EvictionConfig();
            }
            cfg.fillAndClamp();
            if (version > CURRENT_VERSION) {
                EvictionNotice.LOGGER.warn("[Eviction Notice] config was written by a newer version of the mod, leaving the file as it is");
                return cfg;
            }
            cfg.configVersion = CURRENT_VERSION;
            cfg.save(p);
            return cfg;
        }
        return writeDefaults(p);
    }

    private static EvictionConfig writeDefaults(Path p) {
        EvictionConfig cfg = new EvictionConfig();
        cfg.save(p);
        return cfg;
    }

    /** Up to version 1.2.0 all options were at the top level. Old keys are migrated and dropped afterward. */
    private static void migrateFromVersion1(JsonObject obj) {
        JsonObject mobs = new JsonObject();
        if (obj.has("affectZombieVillagers")) {
            mobs.add("zombieVillagers", obj.get("affectZombieVillagers"));
        }
        if (obj.has("exemptIronGolems")) {
            mobs.addProperty("ironGolems", !obj.get("exemptIronGolems").getAsBoolean());
        }
        obj.add("mobs", mobs);
    }

    public void save() {
        save(path());
    }

    public void save(Path p) {
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            EvictionNotice.LOGGER.warn("[Eviction Notice] failed to write config", e);
        }
    }

    /**
     * A group missing from the file keeps its defaults, but "mobs": null makes Gson set it to null,
     and the zone checks would then crash. Such groups get their defaults back here.
     **/
    private void fillAndClamp() {
        if (mobs == null) {
            mobs = new Mobs();
        }
        if (mobs.wanderingTraders == null) {
            mobs.wanderingTraders = new WanderingTraders();
        }
        if (evictionIntervalTicks < 1) {
            evictionIntervalTicks = 1;
        }
    }
}
