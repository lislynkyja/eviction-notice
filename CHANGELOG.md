# Changelog

All notable changes to Eviction Notice are recorded here. Each version heading
matches its release tag (without the leading `v`), and the release workflow
publishes the matching section to the GitHub release and to Modrinth.

## 1.3.0

- Wandering traders can be kept out of zones with `mobs.wanderingTraders.enabled` (default `false`).
- Their trader llamas are kept out with them by default (`mobs.wanderingTraders.llamas`). Trader llamas are never kept out on their own.
- The config now groups its options and has a `configVersion`. Files from 1.2.0 are converted when the game or server starts and keep their settings. `affectZombieVillagers` is now `mobs.zombieVillagers`, and `exemptIronGolems` is now `mobs.ironGolems` with the opposite meaning: `true` keeps iron golems out.
- A config file that can't be read is no longer replaced with the defaults. The mod uses the defaults until the file is fixed.
- Fixed mobs being moved back and forth between two overlapping zones on every check. They're now moved to the nearest spot outside all zones.
- Fixed mobs being moved to the bottom of the world when the spot outside the zone was in an unloaded chunk.
- Fixed mobs being moved onto lava, onto roofs or onto the Nether ceiling. Mobs are now only moved to safe spots, and a mob under a roof or underground stays near its own height. If there's no safe spot in loaded chunks yet, the mob stays until a later check finds one.

## 1.2.0

- Added support for Minecraft 26.1 (26.1, 26.1.1, and 26.1.2). A single build now covers 26.1 through 26.3.
- The mod is now built against 26.1, the oldest supported version, with Fabric API `0.145.1+26.1`.
- Lowered the minimum Fabric Loader to 0.18.4, the same as Fabric API requires on 26.1.
- The version and jar name now carry the supported range (`1.2.0+26.1-26.3`) instead of a single Minecraft version.

### Fixes
- Fixed villagers keeping a bed or workstation claim inside a zone forever when they were in unloaded chunks at the time the zone was created. They would then hang around the zone edge trying to reach it. Zoned claims are now also released when a villager loads in and when the server starts.
- Fixed `/eviction set` silently replacing an existing zone when you reused its name. It now refuses and asks you to remove the old zone first or pick another name.

## 1.1.0

- Added support for Minecraft 26.3. A single build now covers both 26.2 and 26.3.
- Updated the toolchain to match 26.3: Fabric API `0.160.5+26.3`, Fabric Loader `0.19.5`, and the Gradle wrapper to `9.7.1`.
- Corrected the Modrinth "Environment" listing to "Server-side only, works in singleplayer too" (previously client-and-server). This is publish metadata only; the mod still loads on clients so singleplayer and LAN keep working.
- No gameplay or functional changes.

## 1.0.1

- Corrected the author name in the mod metadata.
- Added issue tracker and source repository links to the mod metadata.
- Refreshed the mod icon.
- No gameplay or functional changes.

## 1.0.0

First release, for Minecraft 26.2 (Fabric).

### Features
- Define per-player home zones: `/eviction set <name> <radius> [yMin] [yMax]`, plus `remove`, `list`, and `here`.
- Villagers path around active zones instead of walking into them.
- Beds and workstations inside a zone are hidden from villager searches, so they cannot be found or claimed. Claims that already existed when a zone is created are released automatically.
- A periodic eviction sweep moves any villager that ends up inside a zone to the nearest edge.
- Configurable via `config/eviction_notice.json`: eviction interval, whether zombie villagers are affected, and whether iron golems are exempt (default yes).

### Notes
- Server-side only. Runs on dedicated servers and in singleplayer or LAN. Vanilla clients can join a server running it.
- Requires Fabric API.
