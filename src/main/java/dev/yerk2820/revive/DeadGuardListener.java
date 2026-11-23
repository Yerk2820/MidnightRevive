package dev.yerk2820.revive;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

/** Belt and braces on top of spectator + position lock. Keyed on plugin state, not gamemode. */
final class DeadGuardListener implements Listener {
    private final ReviveManager manager;

    DeadGuardListener(ReviveManager manager) {
        this.manager = manager;
    }

    private boolean dead(Entity e) {
        return e instanceof Player p && manager.isDead(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (!dead(e.getPlayer()) || e.getTo() == null) return;
        // Event mode: while attached to a target the server moves the viewer along with it.
        if (manager.eventMode() && e.getPlayer().getSpectatorTarget() != null) return;
        var f = e.getFrom();
        var t = e.getTo();
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!dead(p) || manager.isInternal(p.getUniqueId())) return;
        // Every teleport of a dead player is blocked unless the plugin itself does it. This includes
        // TeleportCause.SPECTATE (the spectator menu "Teleport to Player" / "Teleport to Team Member"),
        // also when event mode is on: only /watch may move a dead player.
        e.setCancelled(true);
    }

    // The watched player changed world or teleported: bring the viewers along.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTargetWorld(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        manager.followSoon(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetTeleport(PlayerTeleportEvent e) {
        manager.followSoon(e.getPlayer().getUniqueId());
    }

    // Left-clicking an entity or using the spectator menu starts spectating it. Dead players may never do
    // that themselves (event mode on or off); the plugin attaches them from /watch through runInternal().
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStartSpectating(PlayerStartSpectatingEntityEvent e) {
        Player p = e.getPlayer();
        if (!dead(p) || manager.isInternal(p.getUniqueId())) return;
        e.setCancelled(true);
    }

    // Sneaking would normally release the camera. A dead player stays attached until /watch stop or
    // until the target is gone (then enforce() sends them back to limbo).
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent e) {
        Player p = e.getPlayer();
        if (!dead(p) || manager.isInternal(p.getUniqueId())) return;
        if (manager.watchTarget(p) == null) return; // target gone: let go
        e.setCancelled(true);
    }

    // PlayerPortalEvent has its own handler list: a PlayerTeleportEvent listener never sees it,
    // so nether/end portals have to be blocked here explicitly.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent e) {
        if (dead(e.getPlayer()) && !manager.isInternal(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    // Safety net for any other way a dead player ends up in another world (end exit portal, other plugins):
    // put them back into the frozen holding state right away.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (dead(e.getPlayer()) && !manager.isInternal(e.getPlayer().getUniqueId())) {
            manager.enforceNextTick(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (dead(e.getPlayer()) && !manager.isInternal(e.getPlayer().getUniqueId())
                && e.getNewGameMode() != GameMode.SPECTATOR) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onBreak(BlockBreakEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPlace(BlockPlaceEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onInteract(PlayerInteractEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onInteractEntity(PlayerInteractEntityEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onOpen(InventoryOpenEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onClick(InventoryClickEvent e) { if (dead(e.getWhoClicked())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPickup(EntityPickupItemEvent e) { if (dead(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onConsume(PlayerItemConsumeEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onSwap(PlayerSwapHandItemsEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBucketEmpty(PlayerBucketEmptyEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBucketFill(PlayerBucketFillEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBed(PlayerBedEnterEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onFish(PlayerFishEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onFlight(PlayerToggleFlightEvent e) { if (dead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onVehicle(VehicleEnterEvent e) { if (dead(e.getEntered())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBow(EntityShootBowEvent e) { if (dead(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamage(EntityDamageEvent e) { if (dead(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onAttack(EntityDamageByEntityEvent e) { if (dead(e.getDamager())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onTarget(EntityTargetEvent e) { if (dead(e.getTarget())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLaunch(ProjectileLaunchEvent e) {
        Projectile pr = e.getEntity();
        if (pr.getShooter() instanceof Player p && manager.isDead(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent e) {
        if (!dead(e.getPlayer())) return;
        if (!manager.chatAllowed()) {
            e.setCancelled(true);
            manager.send(e.getPlayer(), "chat-blocked");
            return;
        }
        var prefix = manager.chatPrefix();
        if (prefix != null) {
            ChatRenderer old = e.renderer(); // keep other plugins' chat formats working
            e.renderer((source, name, message, viewer) -> old.render(source, prefix.append(name), message, viewer));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!dead(e.getPlayer())) return;
        String m = e.getMessage();
        String label = (m.startsWith("/") ? m.substring(1) : m).split(" ", 2)[0];
        if (!manager.commandAllowed(label)) {
            e.setCancelled(true);
            manager.send(e.getPlayer(), "commands-blocked");
        }
    }
}
