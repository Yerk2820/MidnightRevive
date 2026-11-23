package dev.yerk2820.revive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** All ALIVE/DEAD logic. The PlayerRecord state is the only source of truth. */
final class ReviveManager {
    private final MidnightRevive plugin;
    private final PlayerStore store;
    private final Set<UUID> internal = ConcurrentHashMap.newKeySet();
    private ZoneId zone = ZoneId.systemDefault();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private final Set<UUID> tabMarked = ConcurrentHashMap.newKeySet();
    // Event mode: dead viewer -> the player they are watching.
    private final Map<UUID, UUID> watching = new ConcurrentHashMap<>();
    private LocalTime reviveTime = LocalTime.MIDNIGHT;
    private long lastBoundary = -1;
    private BukkitTask task;
    private CountdownBossBar countdownBossBar;
    private int counter;

    ReviveManager(MidnightRevive plugin, PlayerStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    MidnightRevive plugin() {
        return plugin;
    }

    ZoneId zone() {
        return zone;
    }

    void start() {
        loadConfig();
        countdownBossBar = new CountdownBossBar(this);
        lastBoundary = -1;
        rollover();
        for (Player p : Bukkit.getOnlinePlayers()) handleJoin(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
        if (countdownBossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                countdownBossBar.hide(p);
            }
        }
        for (UUID id : new ArrayList<>(tabMarked)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playerListName(null);
        }
        tabMarked.clear();
    }

    void reload() {
        loadConfig();
        lastBoundary = -1;
        rollover();
        for (Player p : Bukkit.getOnlinePlayers()) handleJoin(p);
    }

    private void loadConfig() {
        String tz = plugin.getConfig().getString("timezone", "system");
        try {
            zone = tz.equalsIgnoreCase("system") ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid timezone '" + tz + "', using system default.");
            zone = ZoneId.systemDefault();
        }
        String rt = plugin.getConfig().getString("revive-time");
        try {
            reviveTime = LocalTime.parse(rt == null ? "00:00" : rt.trim());
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid revive-time '" + rt + "', using 00:00.");
            reviveTime = LocalTime.MIDNIGHT;
        }
    }

    private void tick() {
        rollover();
        // Dead players are re-checked every tick cycle (5 ticks), not just once a second.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isDead(p.getUniqueId())) enforce(p);
        }
        if (++counter % 4 != 0) return;
        int every = Math.max(1, plugin.getConfig().getInt("actionbar.interval-seconds"));
        boolean bar = countdownDisplay().equals("actionbar") && (counter / 4) % every == 0;
        String display = countdownDisplay();

        if (display.equals("bossbar")) {
            countdownBossBar.update();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshTab(p);
            if (display.equals("bossbar") && isDead(p.getUniqueId())) {
                countdownBossBar.show(p);
            } else {
                countdownBossBar.hide(p);
            }
            if (isDead(p.getUniqueId())) {
                if (bar) {
                    Component c;
                    if (eventMode()) {
                        Player t = watchTarget(p);
                        c = t != null ? msg("event-actionbar-watching", "target", t.getName()) : msg("event-actionbar-none");
                    } else {
                        c = msg("actionbar");
                    }
                    if (c != null) p.sendActionBar(c);
                }
            }
        }
    }

    // ---- messages & tab list ----

    Component msg(String key, String... kv) {
        String raw = plugin.getConfig().getString("messages." + key);
        if (raw == null || raw.isEmpty()) return null;
        raw = raw.replace("{time}", untilRevive()).replace("{revive-time}", reviveTime.toString());
        for (int i = 0; i + 1 < kv.length; i += 2) raw = raw.replace("{" + kv[i] + "}", kv[i + 1]);
        return LEGACY.deserialize(raw);
    }

    void send(CommandSender to, String key, String... kv) {
        Component c = msg(key, kv);
        if (c != null) to.sendMessage(c);
    }

    /** Keeps the TAB list name in sync with the DEAD state. */
    void refreshTab(Player p) {
        UUID id = p.getUniqueId();
        boolean want = plugin.getConfig().getBoolean("tablist.enabled") && isDead(id);
        if (want) {
            String prefix = plugin.getConfig().getString("tablist.prefix");
            Component expected = LEGACY.deserialize(prefix == null ? "" : prefix).append(Component.text(p.getName()));
            if (!expected.equals(p.playerListName())) p.playerListName(expected);
            tabMarked.add(id);
        } else if (tabMarked.remove(id)) {
            p.playerListName(null);
        }
    }

    /** Prefix shown before a dead player's name in chat, or null if disabled. */
    Component chatPrefix() {
        if (!plugin.getConfig().getBoolean("chat.prefix-enabled")) return null;
        String prefix = plugin.getConfig().getString("chat.prefix");
        return prefix == null || prefix.isEmpty() ? null : LEGACY.deserialize(prefix);
    }

    boolean chatAllowed() {
        return plugin.getConfig().getBoolean("dead.allow-chat");
    }

    boolean commandAllowed(String label) {
        int colon = label.indexOf(':');
        String l = (colon >= 0 ? label.substring(colon + 1) : label).toLowerCase(java.util.Locale.ROOT);
        if (eventMode() && (l.equals("watch") || l.equals("spectate"))) return true;
        if (!plugin.getConfig().getBoolean("dead.restrict-commands")) return true;
        for (String a : plugin.getConfig().getStringList("dead.allowed-commands")) {
            if (a.equalsIgnoreCase(l)) return true;
        }
        return false;
    }

    boolean isDead(UUID id) {
        PlayerRecord r = store.get(id);
        return r != null && r.dead;
    }

    boolean isInternal(UUID id) {
        return internal.contains(id);
    }

    private ZonedDateTime lastBoundary() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime b = now.toLocalDate().atTime(reviveTime).atZone(zone);
        if (b.isAfter(now)) b = now.toLocalDate().minusDays(1).atTime(reviveTime).atZone(zone);
        return b;
    }

    String untilRevive() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = lastBoundary().toLocalDate()
                .plusDays(1).atTime(reviveTime).atZone(zone);

        long s = Math.max(0, Duration.between(now, next).getSeconds());

        return String.format("%02d:%02d:%02d",
                s / 3600, (s % 3600) / 60, s % 60);
    } // <-- DEZE ACCOLADE ONTBRAK

    float countdownProgress() {
        ZonedDateTime now = ZonedDateTime.now(zone);

        ZonedDateTime previous = lastBoundary();

        ZonedDateTime next = previous.toLocalDate()
                .plusDays(1)
                .atTime(reviveTime)
                .atZone(zone);

        long total = Duration.between(previous, next).getSeconds();
        long remaining = Duration.between(now, next).getSeconds();

        if (total <= 0) return 0.0f;

        float progress = (float) remaining / total;

        return Math.max(0.0f, Math.min(1.0f, progress));
    }

    String reviveTimeText() {
        return reviveTime.toString();
    }

    /** Revives everyone who died before the most recent revive time. Idempotent and cheap. */
    void rollover() {
        long boundary = lastBoundary().toInstant().toEpochMilli();
        if (boundary == lastBoundary) return;
        lastBoundary = boundary;
        reviveBefore(boundary);
    }

    /** Test helper: behaves as if the revive time just happened (revives every dead player). */
    void simulateMidnight() {
        reviveBefore(System.currentTimeMillis() + 1);
    }

    private void reviveBefore(long cutoff) {
        List<UUID> revived = new ArrayList<>();
        for (PlayerRecord r : store.all()) {
            if (r.dead && r.deathTime < cutoff) {
                r.dead = false;
                r.pendingRestore = true;
                revived.add(r.uuid);
            }
        }
        if (revived.isEmpty()) return;
        store.save();
        plugin.getLogger().info("Midnight revival: " + revived.size() + " player(s) are ALIVE again.");
        for (UUID id : revived) {
            PlayerRecord r = store.get(id);
            Component b = msg("broadcast-revive", "player", r == null ? "?" : r.name);
            if (b != null) Bukkit.broadcast(b);
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
        }
    }

    /** Called from PlayerDeathEvent (never fires when a totem saves the player). */
    void registerDeath(Player p) {
        rollover(); // revival at 00:00 always goes first
        PlayerRecord r = store.getOrCreate(p.getUniqueId(), p.getName());
        r.name = p.getName();
        boolean fresh = !r.dead;
        if (fresh) {
            r.dead = true;
            r.deathTime = System.currentTimeMillis();
            r.pendingRestore = false;
        }
        store.save();
        refreshTab(p);
        if (fresh) {
            Component b = msg("broadcast-death", "player", p.getName());
            if (b != null) Bukkit.broadcast(b);
        }
    }

    void handleJoin(Player p) {
        rollover();
        PlayerRecord r = store.getOrCreate(p.getUniqueId(), p.getName());
        if (!p.getName().equals(r.name)) {
            r.name = p.getName();
            store.save();
        }
        refreshTab(p);
        if (r.dead) {
            enforce(p);
            notifyDead(p);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && isDead(p.getUniqueId())) enforce(p);
            });
        } else if (r.pendingRestore) {
            restore(p);
        }
    }

    void notifyDead(Player p) {
        send(p, "dead-notice");

        if (eventMode()) {
            for (String line : plugin.getConfig()
                    .getStringList("messages.event-dead-notice")) {

                p.sendMessage(LEGACY.deserialize(line));
            }
        }
    }

    /** Called from PlayerRespawnEvent for a player whose record is ALIVE. */
    void onAliveRespawn(Player p) {
        PlayerRecord r = store.get(p.getUniqueId());
        if (r != null && r.pendingRestore) {
            r.pendingRestore = false; // vanilla respawn (bed/anchor/world spawn) already applies
            store.save();
            refreshTab(p);
            send(p, "revived");
        }
    }

    void enforceNextTick(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && isDead(p.getUniqueId())) {
                enforce(p);
                notifyDead(p);
            }
        });
    }

    /** Put a DEAD player into the frozen holding state. */
    void enforce(Player p) {
        if (p.isDead()) return; // still on the death screen; respawn event will handle it
        if (eventMode()) {
            enforceEvent(p);
            return;
        }
        watching.remove(p.getUniqueId());
        runInternal(p, () -> {
            if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
            if (p.getSpectatorTarget() != null) p.setSpectatorTarget(null);
            Location limbo = limbo();
            Location cur = p.getLocation();
            if (cur.getWorld() != limbo.getWorld() || cur.distanceSquared(limbo) > 4) {
                p.teleport(limbo, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        });
    }

    // ---------------------------------------------------------------- event mode

    boolean eventMode() {
        return plugin.getConfig().getBoolean("event-mode.enabled", false);
    }

    String countdownDisplay() {
        String fallback = plugin.getConfig().getBoolean("actionbar.enabled", true)
                ? "actionbar" : "off";

        String display = plugin.getConfig()
                .getString("countdown.display", fallback);

        return switch (display == null ? fallback : display.toLowerCase(java.util.Locale.ROOT)) {
            case "bossbar" -> "bossbar";
            case "off" -> "off";
            default -> "actionbar";
        };
    }

    /** A dead viewer may only watch a living, online player in survival-like state. */
    boolean canWatch(Player viewer, Entity target) {
        return target instanceof Player t && !t.equals(viewer) && t.isOnline() && !t.isDead()
                && !isDead(t.getUniqueId()) && t.getGameMode() != GameMode.SPECTATOR;
    }

    /** Living players a dead viewer can watch, sorted by name. */
    List<Player> candidates(Player viewer) {
        List<Player> list = new ArrayList<>();
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (canWatch(viewer, t)) list.add(t);
        }
        list.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** The viewer's current, still valid target, or null. */
    Player watchTarget(Player viewer) {
        UUID id = watching.get(viewer.getUniqueId());
        Player t = id == null ? null : Bukkit.getPlayer(id);
        return t != null && canWatch(viewer, t) ? t : null;
    }

    void setWatching(Player viewer, Player target) {
        watching.put(viewer.getUniqueId(), target.getUniqueId());
    }

    /** Attach the dead viewer's camera to the target (follows it, also across worlds). */
    void watch(Player viewer, Player target) {
        setWatching(viewer, target);
        runInternal(viewer, () -> {
            if (viewer.getGameMode() != GameMode.SPECTATOR) viewer.setGameMode(GameMode.SPECTATOR);
            Location at = target.getLocation();
            if (viewer.getWorld() != at.getWorld() || viewer.getLocation().distanceSquared(at) > 256) {
                viewer.teleport(at, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
            if (viewer.getSpectatorTarget() != target) viewer.setSpectatorTarget(target);
        });
    }

    /** Switch to the next (dir > 0) or previous living player (alphabetical). Only used by /watch next|prev. */
    boolean cycle(Player viewer, int dir) {
        List<Player> c = candidates(viewer);
        if (c.isEmpty()) return false;
        Player cur = watchTarget(viewer);
        int i = cur == null ? -1 : c.indexOf(cur);
        int n = cur == null ? (dir > 0 ? 0 : c.size() - 1) : Math.floorMod(i + dir, c.size());
        watch(viewer, c.get(n));
        return true;
    }

    /** /watch stop: let go of the target and go back to the frozen holding spot. */
    void stopWatching(Player viewer) {
        watching.remove(viewer.getUniqueId());
        enforce(viewer);
    }

    boolean watchByName(Player viewer, String name) {
        for (Player t : candidates(viewer)) {
            if (t.getName().equalsIgnoreCase(name)) {
                watch(viewer, t);
                return true;
            }
        }
        return false;
    }

    /**
     * Event mode: a dead player stays frozen in limbo until they choose someone with /watch.
     * Once attached they stay attached (also through portals and teleports). When the target dies or
     * leaves, the viewer is sent back to limbo and told to pick someone else - never auto-attached.
     */
    private void enforceEvent(Player p) {
        Player target = watchTarget(p);
        if (target != null) {
            watch(p, target);
            return;
        }
        if (watching.remove(p.getUniqueId()) != null) send(p, "event-target-lost");
        runInternal(p, () -> {
            if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
            if (p.getSpectatorTarget() != null) p.setSpectatorTarget(null);
            Location limbo = limbo();
            Location cur = p.getLocation();
            if (cur.getWorld() != limbo.getWorld() || cur.distanceSquared(limbo) > 4) {
                p.teleport(limbo, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        });
    }

    /** A watched player changed world / teleported: re-attach the viewers a moment later. */
    void followSoon(UUID targetId) {
        if (!eventMode()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Map.Entry<UUID, UUID> en : watching.entrySet()) {
                if (!en.getValue().equals(targetId)) continue;
                Player v = Bukkit.getPlayer(en.getKey());
                if (v != null && isDead(v.getUniqueId())) enforce(v);
            }
        }, 2L);
    }

    /** Re-check a dead viewer next tick without the dead-notice message. */
    void enforceSoon(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && isDead(p.getUniqueId())) enforce(p);
        });
    }

    void forget(UUID id) {
        watching.remove(id);
    }

    /** Move a newly ALIVE player to their respawn point and give them back normal play. */
    void restore(Player p) {
        PlayerRecord r = store.get(p.getUniqueId());
        if (r == null || r.dead || !r.pendingRestore) return;
        if (p.isDead()) return; // on death screen: PlayerRespawnEvent will place them
        final Location target;
        if (plugin.getConfig().getBoolean("revive.teleport-to-respawn")) {
            Location dest = p.getPotentialRespawnLocation(); // valid bed/anchor, else null
            target = dest != null ? dest : safeSpawn(mainWorld());
        } else {
            target = null;
        }
        watching.remove(p.getUniqueId());
        runInternal(p, () -> {
            p.setSpectatorTarget(null);
            p.setGameMode(Bukkit.getDefaultGameMode());
            if (target != null) p.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            p.setFallDistance(0f);
            p.setFireTicks(0);
        });
        r.pendingRestore = false;
        store.save();
        refreshTab(p);
        send(p, "revived");
    }

    /** Manual revive. @return true if the player was DEAD. */
    boolean revive(PlayerRecord r) {
        if (!r.dead) return false;
        r.dead = false;
        r.pendingRestore = true;
        store.save();
        Player p = Bukkit.getPlayer(r.uuid);
        if (p != null) restore(p);
        return true;
    }

    private void runInternal(Player p, Runnable action) {
        boolean added = internal.add(p.getUniqueId());
        try {
            action.run();
        } finally {
            if (added) internal.remove(p.getUniqueId());
        }
    }

    World mainWorld() {
        return Bukkit.getWorlds().get(0);
    }

    Location limbo() {
        var cfg = plugin.getConfig();
        String wn = cfg.getString("limbo.world", "");
        if (wn != null && !wn.isBlank()) {
            World w = Bukkit.getWorld(wn);
            if (w != null) {
                return new Location(w, cfg.getDouble("limbo.x", 0.5), cfg.getDouble("limbo.y", 100), cfg.getDouble("limbo.z", 0.5));
            }
            plugin.getLogger().warning("limbo.world '" + wn + "' not found, using main world spawn.");
        }
        return safeSpawn(mainWorld());
    }

    Location safeSpawn(World w) {
        Location s = w.getSpawnLocation();
        int y = w.getHighestBlockYAt(s.getBlockX(), s.getBlockZ()) + 1;
        return new Location(w, s.getBlockX() + 0.5, y, s.getBlockZ() + 0.5, s.getYaw(), s.getPitch());
    }
}
