package dev.yerk2820.revive;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

final class LifecycleListener implements Listener {
    private final ReviveManager manager;

    LifecycleListener(ReviveManager manager) {
        this.manager = manager;
    }

    // Vanilla death is untouched (items/XP drop, death screen shows). We only record it.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        manager.registerDeath(e.getEntity());
    }

    // Clicking "Respawn" can't be blocked through the API, so a dead player is sent
    // straight into the frozen holding state instead of back into the world.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        manager.rollover();
        if (manager.isDead(p.getUniqueId())) {
            e.setRespawnLocation(manager.limbo());
            manager.enforceNextTick(p);
        } else {
            manager.onAliveRespawn(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        manager.handleJoin(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        manager.forget(e.getPlayer().getUniqueId());
    }
}
