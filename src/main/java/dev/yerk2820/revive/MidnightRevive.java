package dev.yerk2820.revive;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MidnightRevive extends JavaPlugin {
    private PlayerStore store;
    private ReviveManager manager;

    @Override
    public void onEnable() {
        ConfigUpdater.update(this);
        store = new PlayerStore(this);
        if (!store.load()) {
            getLogger().severe("players.yml and its backup are unreadable. Disabling so no state is overwritten. "
                    + "Fix or remove the file, then restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        manager = new ReviveManager(this, store);
        getServer().getPluginManager().registerEvents(new LifecycleListener(manager), this);
        getServer().getPluginManager().registerEvents(new DeadGuardListener(manager), this);

        PluginCommand cmd = getCommand("midnightrevive");
        if (cmd != null) {
            ReviveCommand rc = new ReviveCommand(this, manager, store);
            cmd.setExecutor(rc);
            cmd.setTabCompleter(rc);
        }
        PluginCommand watch = getCommand("watch");
        if (watch != null) {
            WatchCommand wc = new WatchCommand(manager);
            watch.setExecutor(wc);
            watch.setTabCompleter(wc);
        }
        manager.start();
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.stop();
        if (store != null) store.save();
    }

    /** Re-reads config.yml and players.yml and re-applies state to online players. */
    boolean reloadAll() {
        reloadConfig();
        if (!store.load()) return false;
        manager.reload();
        return true;
    }
}
