package me.teakivy.teakstweaks.packs.thundershrine;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.Key;
import me.teakivy.teakstweaks.utils.config.Config;
import me.teakivy.teakstweaks.utils.permission.Permission;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.RegionScanner;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ThunderShrine extends BasePack {
    private ScheduledTask particleTask;

    /**
     * Shrine locations are tracked so particles can be spawned on the region that owns the shrine.
     * Markers are entities, so they cannot be iterated from a single thread on Folia.
     */
    private static final Set<Location> shrines = ConcurrentHashMap.newKeySet();

    public ThunderShrine() {
        super(TTPack.THUNDER_SHRINE, Material.NETHER_STAR);
    }

    @Override
    public void init() {
        super.init();
        register();
        scanShrines();
    }

    public void register() {
        if (!getConfig().getBoolean("idle-particles")) return;

        particleTask = Scheduler.runTimer(() -> {
            for (Location location : shrines) {
                Scheduler.runAt(location, () -> {
                    World world = location.getWorld();
                    if (world == null) return;
                    if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
                    world.spawnParticle(Particle.ENCHANT, location.clone().add(0, 1, 0), 1, 0.1, 0.1, 0.1, 1);
                });
            }
        }, 1L, 3L);
    }

    /**
     * Find shrines that already exist in loaded chunks.
     */
    private void scanShrines() {
        for (World world : Bukkit.getWorlds()) {
            RegionScanner.forEachEntity(world, entity -> {
                if (isShrine(entity)) {
                    shrines.add(entity.getLocation().getBlock().getLocation());
                }
            }, null);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isShrine(entity)) {
                shrines.add(entity.getLocation().getBlock().getLocation());
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isShrine(entity)) {
                shrines.remove(entity.getLocation().getBlock().getLocation());
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!Permission.THUNDER_SHRINE_USE.check(event.getPlayer())) return;
        if (item.getType() != Material.getMaterial(getConfig().getString("summoning.summoning-item"))) return;

        Scheduler.runForTimer(event.getItemDrop(), task -> {
            if (isNearShrine(event.getItemDrop().getLocation())) {
                event.getItemDrop().remove();
                startThunder(event.getPlayer(), event.getItemDrop().getLocation());
                task.cancel();
                return;
            }
            if (event.getItemDrop().isDead() || event.getItemDrop().getItemStack().getAmount() != 1) task.cancel();
        }, 1L, 20L);
    }

    public static void startThunder(Player player, Location loc) {
        ConfigurationSection config = Config.get().getConfigurationSection("packs.thunder-shrine");
        World world = player.getWorld();
        if (config.getBoolean("summoning.strike-lightning")) {
            world.strikeLightning(loc.add(0, +1, 0));
        }
        if (config.getBoolean("summoning.show-particles")) {
            world.spawnParticle(Particle.FLAME, loc, 100, 0, 0, 0, .5);
        }
        // Weather is owned by Folia's global region.
        Scheduler.run(() -> {
            world.setWeatherDuration(6000);
            world.setThunderDuration(6000);
            world.setStorm(true);
            world.setThundering(true);
        });
        if (!config.getBoolean("summoning.broadcast-message")) return;

        for (Player oPlayer : Bukkit.getOnlinePlayers()) {
            oPlayer.sendMessage(Component.translatable("thunder_shrine.storm_initialize"));
        }
    }

    public static Set<Location> getShrines() {
        return Set.copyOf(shrines);
    }

    public static boolean isShrine(Entity entity) {
        if (!(entity instanceof Marker)) return false;
        return entity.getPersistentDataContainer().has(Key.get("thunder-shrine"), PersistentDataType.BOOLEAN);
    }

    public static boolean isNearShrine(Location loc) {
        if (loc.getWorld() == null) return false;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
            if (isShrine(entity) && entity.getLocation().distance(loc) < 1) {
                return true;
            }
        }
        return false;
    }

    public static void createShrine(Location loc) throws IOException {
        Marker shrine = (Marker) loc.getWorld().spawnEntity(loc.clone().add(0.5, 0, 0.5), EntityType.MARKER);

        PersistentDataContainer container = shrine.getPersistentDataContainer();
        container.set(Key.get("thunder-shrine"), PersistentDataType.BOOLEAN, true);

        shrines.add(shrine.getLocation().getBlock().getLocation());
    }

    /**
     * Remove a shrine and forget its location.
     */
    public static void removeShrine(Entity shrine) {
        shrines.remove(shrine.getLocation().getBlock().getLocation());
        shrine.remove();
    }

    /**
     * Remove every known shrine. Each shrine is removed on the region that owns it.
     */
    public static void removeAllShrines() {
        for (Location location : List.copyOf(shrines)) {
            shrines.remove(location);
            Scheduler.runAt(location, () -> {
                World world = location.getWorld();
                if (world == null) return;
                for (Entity entity : world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 1, 1, 1)) {
                    if (isShrine(entity)) entity.remove();
                }
            });
        }
    }

    @Override
    public void unregister() {
        super.unregister();
        Scheduler.cancel(particleTask);
        particleTask = null;
    }

}
