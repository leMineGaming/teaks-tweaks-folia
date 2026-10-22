package me.teakivy.teakstweaks.packs.trackstatistics;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.log.Logger;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Objects;

public class TrackStatistics extends BasePack {
    Scoreboard sb;

    Objective playTimeMinutes;
    Objective elytraKm;
    Objective swimKm;
    private boolean enabled = true;

    public TrackStatistics() {
        super(TTPack.TRACK_STATISTICS, Material.MAP);
    }

    @Override
    public void init() {
        super.init();

        try {
            sb = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
            if (playTimeMinutes == null) {
                Objective playTimeMinutes_ = sb.getObjective("hc_playTime");
                if (playTimeMinutes_ == null) {
                    sb.registerNewObjective("hc_playTime", "dummy", "Play Minutes");
                }
                playTimeMinutes = sb.getObjective("hc_playTime");
            }
            if (elytraKm == null) {
                Objective elytraKm_ = sb.getObjective("hc_elytraKm");
                if (elytraKm_ == null) {
                    sb.registerNewObjective("hc_elytraKm", "dummy", "Elytra km");
                }
                elytraKm = sb.getObjective("hc_elytraKm");
            }
            if (swimKm == null) {
                Objective swimKm_ = sb.getObjective("hc_swimKm");
                if (swimKm_ == null) {
                    sb.registerNewObjective("hc_swimKm", "dummy", "Swim km");
                }
                swimKm = sb.getObjective("hc_swimKm");
            }
        } catch (UnsupportedOperationException exception) {
            enabled = false;
            Logger.warning(Component.text("Track Statistics requires the scoreboard API, which Folia does not support. The pack has been disabled."));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        int playTime = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200;
        int elytra = player.getStatistic(Statistic.AVIATE_ONE_CM) / 100000;
        int swim = player.getStatistic(Statistic.SWIM_ONE_CM) / 100000;

        // Scoreboards are global on Folia; keep all writes on the global region thread.
        Scheduler.run(() -> {
            setStatistic(player, playTimeMinutes, playTime);
            setStatistic(player, elytraKm, elytra);
            setStatistic(player, swimKm, swim);
        });
    }

    public void setStatistic(Player player, Objective objective, int amount) {
        objective.getScore(player.getName()).setScore(amount);
    }

}
