# Modpack Settings

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-62b47a?style=flat-square)
![Fabric](https://img.shields.io/badge/Fabric-loader-dbb66e?style=flat-square)
[![License: CC 1.0](https://img.shields.io/badge/license-CC0%201.0-green.svg?style=flat-square)](LICENSE)

Wildeprojekt Modpack Settings is a client-side [Fabric](https://fabricmc.net/) mod for Minecraft **1.20.1**. This loads
and run before MC Initialization and performs the following verifications:

1. **Checks the allocated RAM** before the game starts and warns the player if the launcher gave the JVM less than 8Gb
   of ram.
2. **Keeps configured resource packs up to date** by downloading the latest release assets from
   their GitHub repositories (by default the [Wildeprojekt Overlay](https://github.com/wildeprojekt/Wildeprojekt-Overlay))
   and enabling them ingame.

Both checks run in Fabric's `preLaunch` phase, before Minecraft has loaded. The RAM warning can still be acted
on and the resource packs are already in place by the time the game reads its options. The mod is
`"environment": "client"`.

If run on a server, the mod stays silent - no verification is made.

---

## Configuration

The config lives at:

```
<gameDir>/config/modpacksettings/config.json
```

It is created from the bundled template on first launch. If it is ever malformed, the mod moves it to `config.json.bak`
and recreates it from defaults rather than failing the launch.

### Template

```json
{
  "resourcePacks": [
    {
      "enabled": true,
      "repository": "wildeprojekt/Wildeprojekt-Overlay",
      "assetName": "Wildeprojekt_Overlay.zip",
      "targetFileName": "Wildeprojekt_Overlay.zip",
      "autoEnable": true,
      "force_load_incompatible": false,
      "lastPublishedAt": null,
      "lastActivatedFileName": null
    }
  ]
}
```

Add more objects to the `resourcePacks` array to download and enable multiple packs. Array order is the
activation order. Packs that share a `repository` reuse one GitHub latest-release API call.

### Fields (per entry)

| Key                       | Type          | Default                               | Meaning                                                                                                                  |
|---------------------------|---------------|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `enabled`                 | boolean       | `true`                                | Switch for this pack entry. When `false`, this pack is neither downloaded nor enabled.                                  |
| `repository`              | string        | `"wildeprojekt/Wildeprojekt-Overlay"` | GitHub repository in `owner/repo` form to pull releases from. Blank skips this entry.                                    |
| `assetName`               | string        | `"Wildeprojekt_Overlay.zip"`          | Name of the release asset to download, exactly as it appears on the GitHub release.                                      |
| `targetFileName`          | string        | `"Wildeprojekt_Overlay.zip"`          | Filename written into `resourcepacks/`. Also the name used in `options.txt`.                                             |
| `autoEnable`              | boolean       | `true`                                | Add the pack to the game's enabled resource packs automatically.                                                         |
| `force_load_incompatible` | boolean       | `false`                               | Also whitelist the pack in `incompatibleResourcePacks`, so a pack whose `pack_format` does not match 1.20.1 still loads. |
| `lastPublishedAt`         | string / null | `null`                                | **Written by the mod.** ISO-8601 timestamp of the release last downloaded; used to decide whether an update is needed.   |
| `lastActivatedFileName`   | string / null | `null`                                | **Written by the mod.** Last pack filename added to `options.txt`.                                                       |
| `activationVersion`       | int           | `0`                                   | **Written by the mod.** Migration marker used to backfill activation for existing installs. Leave it alone.              |

The last three are managed state per pack - edit `lastPublishedAt` to `null` (or delete the local zip) if you want to
force a fresh download on the next launch.

---

## Building

Requires a JDK 17+ (the build targets Java 17):

```bash
./gradlew build
```

The jar lands in `build/libs/`. CI (`.github/workflows/build.yml`) runs the same command on every push and pull request
and uploads the jar as an artifact.

Key versions are in `gradle.properties`:

| Property            | Value           |
|---------------------|-----------------|
| `minecraft_version` | 1.20.1          |
| `yarn_mappings`     | 1.20.1+build.10 |
| `loader_version`    | 0.17.3          |
| `fabric_version`    | 0.92.6+1.20.1   |
| `mod_version`       | 0.3.0-1.20.1    |

Lombok is used throughout (`@Getter`, `@Setter`, `@Slf4j`, `@StandardException`); `lombok.config` renames the generated
logger field to `LOGGER`.

### Running in a dev environment

```bash
./gradlew runClient
```

Dev-run state (config, `resourcepacks/`, `options.txt`) is written under `run/`, which is gitignored.

---

## Requirements

- Minecraft `~1.20.1`
- Fabric Loader `>=0.17.3`
- Fabric API
- Java 17 or newer
- Client only

## Project layout

```
src/main/java/org/wildeprojekt/modpacksettings/
├── ModpackSettings.java             # mod id constant
├── ModpackSettingsPrelaunch.java    # preLaunch entrypoint: runs both checks
├── client/
│   ├── ModpackSettingsClient.java   # client entrypoint (logging only)
│   ├── PreLaunchCheck.java          # RAM threshold + updater invocation
│   └── PanelDialog.java             # Swing dialogs (pre-Minecraft UI)
├── config/ModConfig.java            # config load / merge / save
├── i18n/Messages.java               # ResourceBundle lookup
└── resourcepack/
    ├── ResourcePackGrabber.java     # GitHub release fetch, download, validate
    └── ResourcePackActivator.java   # options.txt editing
```

All hand-written classes, fields and methods carry Javadoc; Lombok-generated members are exempt.

## License

[CC0 1.0 Universal](LICENSE) - public domain dedication.
