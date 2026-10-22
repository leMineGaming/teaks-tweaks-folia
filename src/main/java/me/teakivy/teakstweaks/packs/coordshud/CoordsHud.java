package me.teakivy.teakstweaks.packs.coordshud;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.Key;
import me.teakivy.teakstweaks.utils.config.Config;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CoordsHud extends BasePack {

    public CoordsHud() {
        super(TTPack.COORDS_HUD, Material.OBSERVER);
        startHUD();
    }

    static volatile boolean running = false;
    private static final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    public static void startHUD() {
        if (running) return;
        running = true;
        CoordsHudDisplay.init();
        for (Player player : Bukkit.getOnlinePlayers()) {
            startTask(player);
        }
    }

    /**
     * Start the per-player HUD task. Every player gets their own repeating entity task so the HUD
     * is always rendered on the region that owns the player.
     */
    public static void startTask(Player player) {
        if (!running) return;
        if (tasks.containsKey(player.getUniqueId())) return;

        ScheduledTask task = Scheduler.runForTimer(player, scheduledTask -> {
            if (!running) {
                scheduledTask.cancel();
                tasks.remove(player.getUniqueId());
                return;
            }
            if (!player.isOnline()) {
                scheduledTask.cancel();
                tasks.remove(player.getUniqueId());
                return;
            }
            if (!isEnabled(player)) return;
            CoordsHudDisplay.showHud(player);
        }, 1L, 1L);

        if (task != null) tasks.put(player.getUniqueId(), task);
    }

    public static void stopTask(Player player) {
        Scheduler.cancel(tasks.remove(player.getUniqueId()));
    }

    public static void stopHUD() {
        running = false;
        tasks.values().forEach(Scheduler::cancel);
        tasks.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startTask(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopTask(event.getPlayer());
    }

    @Override
    public void unregister() {
        super.unregister();
        stopHUD();
    }

    public static boolean isEnabled(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();

        if (data.has(Key.get("ch_enabled"), PersistentDataType.BOOLEAN)) {
            return data.get(Key.get("ch_enabled"), PersistentDataType.BOOLEAN);
        }

        setEnabled(player, Config.getBoolean("packs.coords-hud.auto-enable") ||
                Config.getBoolean("packs.coords-hud.force-enable"));
        return isEnabled(player);
    }

    public static void setEnabled(Player player, boolean enabled) {
        if (Config.getBoolean("packs.coords-hud.force-enable")) enabled = true;
        PersistentDataContainer data = player.getPersistentDataContainer();

        data.set(Key.get("ch_enabled"), PersistentDataType.BOOLEAN, enabled);

        if (enabled) startTask(player);
        else stopTask(player);
    }
}
