package dev.yerk2820.revive;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Keeps config.yml up to date without the server owner doing anything.
 * <p>
 * The bundled config.yml carries a {@code config-version}. When the file on disk has an older (or no)
 * version: the old file is saved as config.yml.bak-&lt;date&gt;, the new default config (with all comments and
 * new options) is written, and every value the owner had set is copied into it.
 * A config that cannot be parsed at all is backed up and replaced by the default one.
 */
final class ConfigUpdater {
    private ConfigUpdater() {}

    static void update(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
            return;
        }
        int bundled = bundledVersion(plugin);
        YamlConfiguration old = new YamlConfiguration();
        try {
            old.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("config.yml could not be read (" + e.getMessage() + "). "
                    + "Saving it as a backup and writing a fresh default config.");
            if (backup(plugin, file) == null) {
                plugin.getLogger().severe(
                        "Config backup failed. Original config.yml will not be overwritten."
                );
                return;
            }

            plugin.saveResource("config.yml", true);
            return;
        }
        int onDisk = old.getInt("config-version", 0);
        if (onDisk >= bundled) return;

        File bak = backup(plugin, file);
        if (bak == null) {
            plugin.getLogger().severe(
                    "Config backup failed. Update cancelled to protect existing settings."
            );
            return;
        }
        plugin.saveResource("config.yml", true); // overwrites with the new default, comments included
        YamlConfiguration fresh = YamlConfiguration.loadConfiguration(file);

        int kept = 0;
        for (String key : old.getKeys(true)) {
            if (key.equals("config-version") || old.isConfigurationSection(key)) continue;
            if (!fresh.contains(key) || fresh.isConfigurationSection(key)) continue; // option was removed
            fresh.set(key, old.get(key));
            kept++;
        }
        // 4.4.1 and older: the countdown was switched off with actionbar.enabled: false.
        if (!old.contains("countdown.display") && !old.getBoolean("actionbar.enabled", true)) {
            fresh.set("countdown.display", "off");
        }
        try {
            fresh.save(file);
            plugin.getLogger().info("config.yml updated from version " + onDisk + " to " + bundled + ": "
                    + kept + " of your values were kept. Old file saved as " + (bak == null ? "(backup failed)" : bak.getName()) + ".");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save the updated config.yml: " + e.getMessage()
                    + ". The default config is in place; your old values are in the backup file.");
        }
    }

    private static int bundledVersion(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return 0;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getInt("config-version", 0);
        } catch (IOException e) {
            return 0;
        }
    }

    private static File backup(JavaPlugin plugin, File file) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File bak = new File(file.getParentFile(), "config.yml.bak-" + stamp);
        try {
            Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return bak;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not back up config.yml: " + e.getMessage());
            return null;
        }
    }
}
