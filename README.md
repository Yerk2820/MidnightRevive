# MidnightRevive (Paper 26.3, Java 25)

Build: `gradle build` (Gradle 9.1+ for Java 25) -> `build/libs/MidnightRevive-4.3.0.jar` -> drop in `plugins/`.

Notes
- Don't set `hardcore=true`; the plugin replaces that mechanic. Leave keepInventory false.
- Data: plugins/MidnightRevive/players.yml (+ .bak). Timezone and limbo spot: config.yml.
- Clicking Respawn can't be blocked via the API; dead players respawn into a frozen,
  invisible spectator hold at spawn and can't act until 00:00.

1.0.2: dead players can no longer leave limbo through nether/end portals (PlayerPortalEvent is blocked; it is not covered by PlayerTeleportEvent listeners). World changes are re-enforced and dead players are checked every 5 ticks.

## 4.3.0 - event mode reworked
`event-mode.enabled: true` in config.yml (default false). Dead players can then watch living players in first person, but **only through `/watch`**:
- `/watch <player>`, `/watch next`, `/watch prev`, `/watch stop` (alias `/spectate`). Permission `midnightrevive.watch` (default: everyone, only works while dead).
- Nobody is attached automatically anymore (`auto-attach` was removed). A dead player waits frozen in limbo until they use `/watch`.
- Left-click, sneaking and the spectator menu ("Teleport to Player") are blocked for dead players, with event mode on **and** off.
- The camera is locked to the watched player (no free flight) and follows through portals, world changes and teleports.
- Only living players can be watched. If the target dies or leaves, the viewer returns to limbo and sees a message.
Existing config.yml files keep working: options that are missing fall back to the built-in defaults. Replace the file to get the new comments.
