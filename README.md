<p align="center">
    <img src="https://cdn.modrinth.com/data/cached_images/ba03e63d6893f21ccb2dac557809cc42de3e3f93_0.webp" alt="Eviction Notice Banner">
</p>
<h1 align="center">Eviction Notice</h1>
<p align="center">
    <a href="https://modrinth.com/mod/eviction-notice" target="_blank">
        <img src="https://img.shields.io/modrinth/v/eviction-notice?style=for-the-badge&logo=modrinth&logoSize=auto&logoColor=white&color=00AF5C&label=latest" alt="Latest version">
    </a>
    <a href="https://modrinth.com/mod/eviction-notice" target="_blank">
        <img src="https://img.shields.io/modrinth/game-versions/eviction-notice?style=for-the-badge&logo=modrinth&logoSize=auto&logoColor=white&color=00AF5C&label=minecraft" alt="Minecraft versions">
    </a>
</p>
<p align="center">
    <a href="https://modrinth.com/mod/eviction-notice" target="_blank">
        <img src="https://img.shields.io/modrinth/dt/eviction-notice?style=for-the-badge&logo=modrinth&logoSize=auto&logoColor=white&color=00AF5C" alt="Modrinth downloads">
    </a>
    <a href="https://modrinth.com/mod/eviction-notice" target="_blank">
        <img src="https://img.shields.io/modrinth/followers/eviction-notice?style=for-the-badge&logo=modrinth&logoColor=white&logoSize=auto&color=00AF5C" alt="Modrinth followers">
    </a>
    <a href="LICENSE">
        <img src="https://img.shields.io/badge/license-MIT-00AF5C?style=for-the-badge" alt="License">
    </a>
</p>
<p align="center">Keep villagers out of your house without iron doors. This mod lets you define a zone around your house that keeps villagers away and forces them to leave your beds and workstations alone.</p>

## Features

- Villagers walk around your zone instead of through it
- Beds and workbench blocks within a zone cannot be claimed
- Villagers inside the zone are teleported out
- Optional height limit for the zone to accommodate basements or individual floors
- Each player has their own zones
- Server-side only: Players joining the server do not need the mod to use it
- Mobs affected by the zone are partially configurable


## Installation

1. Install Fabric Loader 0.18.4 or newer for Minecraft 26.1–26.3.
2. Put Eviction Notice and Fabric API in your mods folder.
3. For a server, only the server needs it.

## Usage

> [!WARNING]
> Villagers that lose their workstation to a zone also lose their profession if you haven't traded with them yet, the same as breaking the workstation in vanilla.

### Commands

| Command                                       | What it does                                  |
|-----------------------------------------------|-----------------------------------------------|
| `/eviction set <name> <radius> [yMin] [yMax]` | Creates a zone centred on you                 |
| `/eviction list`                              | Lists your zones                              |
| `/eviction here`                              | Shows if you're standing in one of your zones |
| `/eviction remove <name>`                     | Deletes a zone                                |

The radius goes up to 256. Leave out `yMin` and `yMax` to cover the full height.

### Configuration

The config is at `config/eviction_notice.json`. Restart the game or server after changing it.

| Option                            | Default | What it does                                                                                       |
|-----------------------------------|:-------:|----------------------------------------------------------------------------------------------------|
| `evictionIntervalTicks`           |   `20`  | How often each zone is checked, in ticks (20 ticks = 1 second)                                     |
| `mobs.zombieVillagers`            |  `true` | Keeps zombie villagers out as well                                                                 |
| `mobs.ironGolems`                 | `false` | Keeps iron golems out as well                                                                      |
| `mobs.wanderingTraders.enabled`   | `false` | Keeps wandering traders out as well                                                                |
| `mobs.wanderingTraders.llamas`    |  `true` | Keeps their trader llamas out too. Only works while `enabled` is on, llamas are never kept out on their own |

On each check, mobs inside a zone are moved out and villagers inside lose any bed or workstation they claimed in a zone.

Config files from version 1.2.0 or older are converted to the new layout when the game or server starts and your settings are kept. If the file can't be read, for example because of a typo, the mod uses the defaults and leaves the file alone so you can fix it.

<details>
<summary>Default config</summary>

```json
{
  "configVersion": 2,
  "evictionIntervalTicks": 20,
  "mobs": {
    "zombieVillagers": true,
    "ironGolems": false,
    "wanderingTraders": {
      "enabled": false,
      "llamas": true
    }
  }
}
```

</details>

## Roadmap

Planned, in no fixed order:

- [ ] Server controls, such as admin commands, permissions, zone limits and protection against griefing
- [ ] Seeing where a zone ends in the game
- [ ] An easier way to set up a zone, with a preview
- [ ] Possible backport to version 1.21.X

## Bugs and ideas

**Something not working?** Open a [bug report](https://github.com/lislynkyja/eviction-notice/issues/new?template=1_bug_report.yml). Please include your versions, your mod list and `logs/latest.log`, that makes it a lot easier to find the problem.

**Game crashed?** Open a [crash report](https://github.com/lislynkyja/eviction-notice/issues/new?template=2_crash_report.yml) with the newest file from `crash-reports/`.

**Got an idea?** [Suggest a feature](https://github.com/lislynkyja/eviction-notice/issues/new?template=3_feature_request.yml). Check the roadmap above first, it might already be planned.

**Anything else?** [Ask a question](https://github.com/lislynkyja/eviction-notice/issues/new?template=4_question.yml), or use [something else](https://github.com/lislynkyja/eviction-notice/issues/new?template=5_other.yml) if nothing above fits.


## License

MIT, see [LICENSE](LICENSE)
