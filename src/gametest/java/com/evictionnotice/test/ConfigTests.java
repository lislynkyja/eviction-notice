package com.evictionnotice.test;

import com.evictionnotice.config.EvictionConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loading the config file. Each test uses its own file in {@code config_tests/}, so they don't
 * touch the config the other tests run with.
 */
public class ConfigTests {

    @GameTest
    public void oldConfigIsMigrated(GameTestHelper helper) {
        Path file = write("old.json", """
                {
                  "evictionIntervalTicks": 40,
                  "affectZombieVillagers": false,
                  "exemptIronGolems": false
                }""");
        EvictionConfig cfg = EvictionConfig.load(file);

        helper.assertTrue(cfg.evictionIntervalTicks == 40, "evictionIntervalTicks wasn't kept");
        helper.assertFalse(cfg.mobs.zombieVillagers, "affectZombieVillagers wasn't moved");
        helper.assertTrue(cfg.mobs.ironGolems, "exemptIronGolems wasn't moved and inverted");
        helper.assertFalse(cfg.mobs.wanderingTraders.enabled, "Wandering traders aren't off by default");

        JsonObject saved = read(file);
        helper.assertTrue(saved.get("configVersion").getAsInt() == EvictionConfig.CURRENT_VERSION, "configVersion wasn't written");
        helper.assertFalse(saved.has("affectZombieVillagers"), "The old key affectZombieVillagers is still in the file");
        helper.assertFalse(saved.has("exemptIronGolems"), "The old key exemptIronGolems is still in the file");
        helper.assertTrue(saved.getAsJsonObject("mobs").getAsJsonObject("wanderingTraders").has("llamas"),
                "The new options weren't written");
        helper.succeed();
    }

    @GameTest
    public void missingOptionsGetDefaults(GameTestHelper helper) {
        Path file = write("partial.json", """
                {
                  "configVersion": 2,
                  "mobs": { "ironGolems": true }
                }""");
        EvictionConfig cfg = EvictionConfig.load(file);

        helper.assertTrue(cfg.mobs.ironGolems, "ironGolems wasn't read");
        helper.assertTrue(cfg.mobs.zombieVillagers, "zombieVillagers didn't get its default");
        helper.assertTrue(cfg.evictionIntervalTicks == 20, "evictionIntervalTicks didn't get its default");
        helper.assertTrue(cfg.mobs.wanderingTraders.llamas, "llamas didn't get its default");
        helper.assertTrue(read(file).getAsJsonObject("mobs").has("wanderingTraders"), "The missing options weren't written");
        helper.succeed();
    }

    @GameTest
    public void brokenConfigIsLeftAlone(GameTestHelper helper) {
        String broken = "{ \"evictionIntervalTicks\": 40, }}";
        Path file = write("broken.json", broken);
        EvictionConfig cfg = EvictionConfig.load(file);

        helper.assertTrue(cfg.evictionIntervalTicks == 20, "A broken file didn't give the defaults");
        helper.assertTrue(readString(file).equals(broken), "The broken file was overwritten");
        helper.succeed();
    }

    @GameTest
    public void newerConfigIsLeftAlone(GameTestHelper helper) {
        String newer = """
                {
                  "configVersion": 99,
                  "evictionIntervalTicks": 40,
                  "optionFromTheFuture": true
                }""";
        Path file = write("newer.json", newer);
        EvictionConfig cfg = EvictionConfig.load(file);

        helper.assertTrue(cfg.evictionIntervalTicks == 40, "The known options weren't read");
        helper.assertTrue(readString(file).equals(newer), "The file from a newer version was overwritten");
        helper.succeed();
    }

    private static Path write(String name, String content) {
        Path file = FabricLoader.getInstance().getGameDir().resolve("config_tests").resolve(name);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonObject read(Path file) {
        return JsonParser.parseString(readString(file)).getAsJsonObject();
    }
}
