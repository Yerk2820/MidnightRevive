package dev.yerk2820.revive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ReviveCommand implements TabExecutor {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MidnightRevive plugin;
    private final ReviveManager manager;
    private final PlayerStore store;

    ReviveCommand(MidnightRevive plugin, ReviveManager manager, PlayerStore store) {
        this.plugin = plugin;
        this.manager = manager;
        this.store = store;
    }

    private void msg(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(Component.text(text, c));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            msg(sender, "Usage: /" + label + " <status|revive <player>|reload|debug <player>>", NamedTextColor.YELLOW);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String perm = sub.equals("status") ? "midnightrevive.admin"
                : sub.equals("testmidnight") ? "midnightrevive.debug" : "midnightrevive." + sub;
        if (!List.of("status", "revive", "reload", "debug", "testmidnight").contains(sub)) {
            msg(sender, "Unknown subcommand.", NamedTextColor.RED);
            return true;
        }
        if (!sender.hasPermission(perm)) {
            msg(sender, "You don't have permission.", NamedTextColor.RED);
            return true;
        }
        manager.rollover();
        switch (sub) {
            case "status" -> status(sender);
            case "testmidnight" -> {
                manager.simulateMidnight();
                msg(sender, "Simulated 00:00: all dead players revived.", NamedTextColor.GREEN);
            }
            case "reload" -> {
                if (plugin.reloadAll()) msg(sender, "MidnightRevive reloaded.", NamedTextColor.GREEN);
                else msg(sender, "Reload failed: players.yml unreadable. Kept in-memory state.", NamedTextColor.RED);
            }
            case "revive", "debug" -> {
                if (args.length < 2) {
                    msg(sender, "Usage: /" + label + " " + sub + " <player>", NamedTextColor.YELLOW);
                    return true;
                }
                PlayerRecord r = find(args[1]);
                if (r == null) {
                    msg(sender, "No record for '" + args[1] + "'.", NamedTextColor.RED);
                    return true;
                }
                if (sub.equals("revive")) {
                    if (manager.revive(r)) msg(sender, r.name + " has been revived.", NamedTextColor.GREEN);
                    else msg(sender, r.name + " is not dead.", NamedTextColor.YELLOW);
                } else {
                    debug(sender, r);
                }
            }
            default -> { }
        }
        return true;
    }

    private PlayerRecord find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return store.getOrCreate(online.getUniqueId(), online.getName());
        return store.findByName(name);
    }

    private void status(CommandSender s) {
        List<PlayerRecord> dead = new ArrayList<>();
        int alive = 0;
        for (PlayerRecord r : store.all()) {
            if (r.dead) dead.add(r); else alive++;
        }
        msg(s, "Dead: " + dead.size() + " | Alive: " + alive + " | Revival in " + manager.untilRevive()
                + " (" + manager.zone() + ")", NamedTextColor.GOLD);
        for (PlayerRecord r : dead) {
            String online = Bukkit.getPlayer(r.uuid) != null ? "online" : "offline";
            msg(s, " - " + r.name + " (" + online + ") died " + FMT.format(Instant.ofEpochMilli(r.deathTime).atZone(manager.zone())),
                    NamedTextColor.GRAY);
        }
    }

    private void debug(CommandSender s, PlayerRecord r) {
        msg(s, "--- " + r.name + " ---", NamedTextColor.GOLD);
        msg(s, "UUID: " + r.uuid, NamedTextColor.GRAY);
        msg(s, "State: " + (r.dead ? "DEAD" : "ALIVE") + " | pendingRestore: " + r.pendingRestore, NamedTextColor.GRAY);
        msg(s, "Death time: " + (r.deathTime > 0
                ? FMT.format(Instant.ofEpochMilli(r.deathTime).atZone(manager.zone())) : "never"), NamedTextColor.GRAY);
        Player p = Bukkit.getPlayer(r.uuid);
        if (p == null) {
            msg(s, "Offline", NamedTextColor.GRAY);
            return;
        }
        var l = p.getLocation();
        msg(s, "Online | gamemode: " + p.getGameMode() + " | isDead(vanilla): " + p.isDead(), NamedTextColor.GRAY);
        msg(s, String.format("Location: %s %.1f %.1f %.1f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ()), NamedTextColor.GRAY);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("status", "revive", "reload", "debug", "testmidnight")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("revive") || args[0].equalsIgnoreCase("debug"))) {
            for (PlayerRecord r : store.all()) {
                if (r.name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(r.name);
            }
        }
        return out;
    }
}
