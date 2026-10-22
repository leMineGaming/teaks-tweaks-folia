package me.teakivy.teakstweaks.packs.countmobdeaths;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.log.Logger;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Objects;

public class CountMobDeaths extends BasePack {

    public CountMobDeaths() {
        super(TTPack.COUNT_MOB_DEATHS, Material.ECHO_SHARD);
    }

    Objective objective;
    private boolean enabled = true;

    @Override
    public void init() {
        super.init();
        try {
            Scoreboard board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
            if (board.getObjective("mobDeathCount") == null) {
                board.registerNewObjective("mobDeathCount", Criteria.DUMMY, getText("objective_name"));
            }
            objective = board.getObjective("mobDeathCount");
        } catch (UnsupportedOperationException exception) {
            enabled = false;
            Logger.warning(Component.text("Count Mob Deaths requires the scoreboard API, which Folia does not support. The pack has been disabled."));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!enabled || objective == null) return;
        if (event.getEntity().getType() == EntityType.PLAYER) return;

        String name = event.getEntity().getName();
        // The main scoreboard is global data on Folia, so it is only touched on the global region.
        Scheduler.run(() -> {
            if (objective == null) return;
            objective.getScore(name).setScore(objective.getScore(name).getScore() + 1);
        });
    }

}
