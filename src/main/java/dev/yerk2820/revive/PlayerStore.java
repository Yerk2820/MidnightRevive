package dev.yerk2820.revive;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** players.yml persistence: fsync'd temp file + atomic rename + .bak copy. */
final class PlayerStore {
    private final MidnightRevive plugin;
    private final File file;
    private final File backup;
    private final Map<UUID, PlayerRecord> records = new HashMap<>();

    PlayerStore(MidnightRevive plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.backup = new File(plugin.getDataFolder(), "players.yml.bak");
    }

    /** @return false if data exists but could not be read (nothing is changed then). */
    synchronized boolean load() {
        if (!file.exists() && !backup.exists()) {
            records.clear();
            return true;
        }
        Map<UUID, PlayerRecord> parsed = parse(file);
        if (parsed == null) {
            plugin.getLogger().severe("players.yml unreadable, trying players.yml.bak");
            parsed = parse(backup);
        }
        if (parsed == null) return false;
        records.clear();
        records.putAll(parsed);
        return true;
    }

    private Map<UUID, PlayerRecord> parse(File f) {
        if (!f.exists()) return null;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(f);
            Map<UUID, PlayerRecord> out = new HashMap<>();
            ConfigurationSection sec = yaml.getConfigurationSection("players");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(key);
                    if (s == null) continue;
                    UUID id = UUID.fromString(key);
                    PlayerRecord r = new PlayerRecord(id, s.getString("name", "?"));
                    r.dead = "DEAD".equalsIgnoreCase(s.getString("state", "ALIVE"));
                    r.deathTime = s.getLong("death-time", 0L);
                    r.pendingRestore = s.getBoolean("pending-restore", false);
                    out.put(id, r);
                }
            }
            return out;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to parse " + f.getName() + ": " + e);
            return null;
        }
    }

    synchronized PlayerRecord get(UUID id) {
        return records.get(id);
    }

    synchronized PlayerRecord getOrCreate(UUID id, String name) {
        PlayerRecord r = records.get(id);
        if (r == null) {
            r = new PlayerRecord(id, name);
            records.put(id, r);
            save();
        }
        return r;
    }

    synchronized Collection<PlayerRecord> all() {
        return new ArrayList<>(records.values());
    }

    synchronized PlayerRecord findByName(String name) {
        for (PlayerRecord r : records.values()) {
            if (r.name.equalsIgnoreCase(name)) return r;
        }
        return null;
    }

    synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerRecord r : records.values()) {
            String p = "players." + r.uuid + ".";
            yaml.set(p + "name", r.name);
            yaml.set(p + "state", r.dead ? "DEAD" : "ALIVE");
            yaml.set(p + "death-time", r.deathTime);
            yaml.set(p + "death-time-readable", r.deathTime > 0 ? Instant.ofEpochMilli(r.deathTime).toString() : "never");
            yaml.set(p + "pending-restore", r.pendingRestore);
        }
        try {
            plugin.getDataFolder().mkdirs();
            Path tmp = new File(plugin.getDataFolder(), "players.yml.tmp").toPath();
            byte[] bytes = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(ByteBuffer.wrap(bytes));
                ch.force(true);
            }
            if (file.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save players.yml: " + e);
        }
    }
}
