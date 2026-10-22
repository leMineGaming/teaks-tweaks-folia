package me.teakivy.teakstweaks.packs.alwaysactivebeacons;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public class AlwaysActiveBeacons extends BasePack {

    public AlwaysActiveBeacons() {
        super(TTPack.ALWAYS_ACTIVE_BEACONS, Material.BEACON);
    }

    public void init() {
        super.init();
        start();
    }

    public void start() {
        Scheduler.runTimer(() -> {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    // Tile entities may only be read from the region that owns the chunk.
                    Scheduler.runAt(new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4), () -> applyBeacons(chunk));
                }
            }
        }, 1L, 100L); // every 5 seconds
    }

    private void applyBeacons(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() != Material.BEACON) continue;

            Beacon beacon = (Beacon) state.getBlock().getState();
            if (beacon.getTier() == 0) continue;

            List<LivingEntity> entities = (List<LivingEntity>) beacon.getEntitiesInRange();
            PotionEffect primaryEffect = beacon.getPrimaryEffect();
            PotionEffect secondaryEffect = beacon.getSecondaryEffect();

            for (LivingEntity entity : entities) {
                if (entity instanceof Player player) {
                    if (primaryEffect != null) {
                        player.addPotionEffect(primaryEffect);
                    }
                    if (secondaryEffect != null) {
                        player.addPotionEffect(secondaryEffect);
                    }
                }
            }
        }
    }
}
