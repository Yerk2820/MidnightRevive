# 🌙 MidnightRevive

**Death isn't the end... but you'll have to wait until midnight.**

MidnightRevive is a Minecraft plugin for **Paper 26.3** that introduces a unique death system to your server. Players who die must wait until midnight before they can return to the game.

## ✨ Features

- **Midnight Revival** – Dead players are revived at 00:00.
- **Spectator Limbo** – Dead players are held at the world's spawn point while waiting.
- **Event Mode** – Allows dead players to watch living players through the `/watch` command.
- **Configurable** – Customize the plugin through `config.yml`.
- **Dead Player Restrictions** – Prevents dead players from interacting or escaping limbo.

## 📦 Requirements

- Minecraft **Paper 26.3**
- **Java 25**
- **Gradle 9.1+** (for building from source)

## 🔧 Installation

1. Download the latest `MidnightRevive` release.
2. Place the `.jar` file in your server's `plugins/` folder.
3. Restart your server.
4. Configure the plugin in `plugins/MidnightRevive/config.yml`.

## 🛠️ Building from Source

Clone or download this repository, then run:

```bash
gradle build
```

The compiled plugin will be located at:

```text
build/libs/MidnightRevive-4.4.2.jar
```

Place this `.jar` file in your server's `plugins/` folder.

## ⚙️ Important Configuration Notes

- **Do not** set `hardcore=true`. MidnightRevive replaces that mechanic.
- Keep `keepInventory` set to `false`.
- Player data is stored in `plugins/MidnightRevive/players.yml`.
- A backup is also maintained as `players.yml.bak`.
- The timezone and limbo location can be configured in `config.yml`.

### How the Respawn System Works

Minecraft's API does not allow the plugin to block the Respawn button itself. Instead, dead players respawn into a frozen, invisible spectator state at the world's spawn point.

They cannot interact or move freely until the next revival at **00:00**.

## 👁️ Event Mode

Event Mode allows dead players to watch living players in first person.

Enable it in `config.yml`:

```yaml
event-mode:
  enabled: true
```

It is disabled by default.

### Commands

| Command | Description |
|---|---|
| `/watch <player>` | Watch a specific player |
| `/watch next` | Watch the next available player |
| `/watch prev` | Watch the previous player |
| `/watch stop` | Stop watching and return to limbo |

`/spectate` is an alias for `/watch`.

**Permission:** `midnightrevive.watch`

By default, everyone has this permission. The commands only work while the player is dead.

### Event Mode Details

- Dead players are never attached to a player automatically.
- Players remain frozen in limbo until they use `/watch`.
- Left-clicking, sneaking and the spectator menu's **Teleport to Player** option are blocked for dead players.
- The camera is locked to the watched player; free flight is disabled.
- The camera follows the target through portals, world changes and teleports.
- Only living players can be watched.
- If the watched player dies or leaves, the viewer returns to limbo and receives a message.

## 📋 Version History

### 4.4.2 – Automatic Config Updates

- `config.yml` is now updated automatically. When a new version changes the config, the old file is saved as `config.yml.bak-<date>`, the new config (with all comments) is written, and your own values are copied over. Nobody has to delete the old config anymore.
- `countdown.display` (`actionbar`, `bossbar`, `off`) is now the only switch for the countdown. `actionbar.enabled` was removed; old configs with `actionbar.enabled: false` are migrated to `countdown.display: "off"`.
- In event mode the `/watch` info stays visible in the actionbar when the bossbar is used.
- Added a credits section to the config.

### 4.4.1 – Countdown Bossbar

- Added the countdown bossbar and the event mode help message for dead players.

### 4.3.0 – Event Mode Reworked

- Reworked Event Mode.
- Added manual player watching through `/watch`.
- Removed automatic player attachment (`auto-attach`).
- Improved camera locking and tracking.
- Added additional restrictions for dead players.

### 1.0.2 – Portal Protection

- Dead players can no longer escape limbo through Nether or End portals.
- `PlayerPortalEvent` is blocked separately because `PlayerTeleportEvent` listeners do not cover it.
- World changes are re-enforced.
- Dead players are checked every 5 ticks.

## 🔄 Configuration Compatibility

You never need to delete your old `config.yml`. On startup MidnightRevive compares the `config-version` in your file with the one in the plugin. If it is older, your file is backed up (`config.yml.bak-<date>`), replaced by the new default config with all new options and comments, and your own values are copied into it. Options that were removed are dropped; the backup keeps everything.

---

**Enjoy MidnightRevive!** 🌙
