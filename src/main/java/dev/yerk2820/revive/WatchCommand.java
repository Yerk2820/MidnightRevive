package dev.yerk2820.revive;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /watch <player|next|prev|stop> - the ONLY way a dead player can attach to somebody (event mode only).
 * Left-click, sneaking and the spectator menu are all blocked in {@link DeadGuardListener}.
 */
final class WatchCommand implements TabExecutor {
    private final ReviveManager manager;

    WatchCommand(ReviveManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p) || !manager.eventMode() || !manager.isDead(p.getUniqueId())) {
            if (sender instanceof Player) manager.send(sender, "event-not-available");
            return true;
        }
        if (args.length == 0) {
            manager.send(p, "event-usage");
            return true;
        }
        String a = args[0];
        switch (a.toLowerCase(Locale.ROOT)) {
            case "stop", "leave" -> {
                manager.stopWatching(p);
                manager.send(p, "event-stopped");
            }
            case "next" -> {
                if (!manager.cycle(p, 1)) manager.send(p, "event-no-target");
            }
            case "prev", "previous" -> {
                if (!manager.cycle(p, -1)) manager.send(p, "event-no-target");
            }
            default -> {
                if (!manager.watchByName(p, a)) manager.send(p, "event-no-target");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1 || !(sender instanceof Player p) || !manager.eventMode()
                || !manager.isDead(p.getUniqueId())) return out;
        String pre = args[0].toLowerCase(Locale.ROOT);
        for (String s : List.of("next", "prev", "stop")) if (s.startsWith(pre)) out.add(s);
        for (Player t : manager.candidates(p)) if (t.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(t.getName());
        return out;
    }
}
