package me.teakivy.teakstweaks.packs.chunkloaders;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.config.Config;
import me.teakivy.teakstweaks.utils.permission.Permission;
import me.teakivy.teakstweaks.utils.register.TTPack;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;

public class ChunkLoaders extends BasePack {

    public ChunkLoaders() {
        super(TTPack.CHUNK_LOADERS, Material.LODESTONE);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!Permission.CHUNK_LOADERS.check(event.getPlayer())) return;

        Item item = event.getItemDrop();
        if (!item.getItemStack().getType().toString().equalsIgnoreCase(getConfig().getString("cost"))) return;

        Scheduler.runForTimer(item, task -> {
                Block block = item.getLocation().add(0, -0.5, 0).getBlock();
                if (block.getType() != Material.LODESTONE) return;

                if (event.getItemDrop().isDead() || event.getItemDrop().getItemStack().getAmount() < 1) {
                    task.cancel();
                    return;
                }

                // Chunk tickets are global state on Folia, so the force-load check/update has to run
                // on the global region thread.
                org.bukkit.World world = block.getWorld();
                int chunkX = block.getX() >> 4;
                int chunkZ = block.getZ() >> 4;
                Scheduler.run(() -> {
                    if (world.isChunkForceLoaded(chunkX, chunkZ)) {
                        task.cancel();
                        event.getPlayer().sendMessage(getText("already_loaded"));
                        return;
                    }

                    world.setChunkForceLoaded(chunkX, chunkZ, true);
                    Scheduler.runAt(block.getLocation(), () -> {
                        Marker marker = (Marker) world.spawnEntity(block.getLocation().add(.5, 1, .5), EntityType.MARKER);
                        marker.addScoreboardTag("loaded");

                        if (Config.getBoolean("packs.chunk-loaders.show-particles")) {
                            world.spawnParticle(Particle.FLAME, item.getLocation(), 100, 0, 0, 0, .5);
                        }

                        ItemStack newItem = item.getItemStack().clone();
                        newItem.setAmount(newItem.getAmount() - 1);
                        item.setItemStack(newItem);

                        event.getPlayer().sendMessage(getText("loaded"));

                        if (event.getItemDrop().isDead() || event.getItemDrop().getItemStack().getAmount() < 1) task.cancel();
                    });
                });
        }, 1L, 20L);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.LODESTONE) return;
        Block block = event.getBlock();

        for (Entity nearbyEntity : block.getWorld().getNearbyEntities(block.getLocation().add(.5, 1, .5), .1, .1, .1)) {
            if (!nearbyEntity.getScoreboardTags().contains("loaded") || nearbyEntity.getType() != EntityType.MARKER) continue;

            int chunkX = block.getX() >> 4;
            int chunkZ = block.getZ() >> 4;
            Scheduler.run(() -> block.getWorld().setChunkForceLoaded(chunkX, chunkZ, false));
            nearbyEntity.remove();
            return;
        }
    }

}
