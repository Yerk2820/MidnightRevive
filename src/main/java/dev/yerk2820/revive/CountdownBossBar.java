package dev.yerk2820.revive;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Locale;

public class CountdownBossBar {

    private final ReviveManager manager;
    private final BossBar bossBar;

    public CountdownBossBar(ReviveManager manager) {
        this.manager = manager;

        this.bossBar = BossBar.bossBar(
                Component.text("Revival countdown"),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
    }

    public void update() {
        String time = manager.untilRevive();

        String text = manager.plugin().getConfig()
                .getString("bossbar.text", "&c☽ Revival in {time}");

        text = text.replace("{time}", time);

        bossBar.name(
                LegacyComponentSerializer.legacyAmpersand().deserialize(text)
        );

        String color = manager.plugin().getConfig()
                .getString("bossbar.color", "RED");

        try {
            bossBar.color(BossBar.Color.valueOf(
                    color.toUpperCase(Locale.ROOT)
            ));
        } catch (IllegalArgumentException e) {
            bossBar.color(BossBar.Color.RED);
        }

        String style = manager.plugin().getConfig()
                .getString("bossbar.style", "PROGRESS");

        try {
            bossBar.overlay(BossBar.Overlay.valueOf(
                    style.equalsIgnoreCase("SOLID")
                            ? "PROGRESS"
                            : style.toUpperCase(Locale.ROOT)
            ));
        } catch (IllegalArgumentException e) {
            bossBar.overlay(BossBar.Overlay.PROGRESS);
        }

        bossBar.progress(manager.countdownProgress());
    }

    public void show(Player player) {
        player.showBossBar(bossBar);
    }

    public void hide(Player player) {
        player.hideBossBar(bossBar);
    }
}