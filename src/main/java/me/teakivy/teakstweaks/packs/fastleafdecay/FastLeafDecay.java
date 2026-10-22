package me.teakivy.teakstweaks.packs.fastleafdecay;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class FastLeafDecay extends BasePack {

    // Code By: @StarTux on Github
    // Couldn't find where to contact you
    // If you have any issues with this, DM me @TeakIvyYT on Twitter
    // Thanks!

    public FastLeafDecay() {
        super(TTPack.FAST_LEAF_DECAY, Material.OAK_LEAVES);
    }

    private final Set<Block> scheduledBlocks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean oneByOneScheduled = new AtomicBoolean(false);
    private static final List<BlockFace> NEIGHBORS = Arrays
            .asList(BlockFace.UP,
                    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                    BlockFace.DOWN);


    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        onBlockRemove(event.getBlock(), getConfig().getLong("break-delay"));
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        onBlockRemove(event.getBlock(), getConfig().getLong("decay-delay"));
    }


    private void onBlockRemove(final Block oldBlock, long delay) {
        if (!Tag.LOGS.isTagged(oldBlock.getType())
                && !Tag.LEAVES.isTagged(oldBlock.getType())) {
            return;
        }
        // No return
        List<BlockFace> faces = new ArrayList<>(NEIGHBORS);
        Collections.shuffle(faces);
        for (BlockFace neighborFace: faces) {
            final Block block = oldBlock.getRelative(neighborFace);
            if (!block.getType().toString().toLowerCase().contains("leaves")) continue;
            Leaves leaves = (Leaves) block.getBlockData();
            if (leaves.isPersistent()) continue;
            if (scheduledBlocks.contains(block)) continue;
            if (getConfig().getBoolean("one-by-one")) {
                scheduledBlocks.add(block);
                scheduleOneByOne(delay);
            } else {
                scheduledBlocks.add(block);
                Scheduler.runAtLater(block.getLocation(), () -> decay(block), delay);
            }
        }
    }

    private boolean decay(Block block) {
        if (!scheduledBlocks.remove(block)) return false;
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return false;
        if (!Tag.LEAVES.isTagged(block.getType())) return false;
        Leaves leaves = (Leaves) block.getBlockData();
        if (leaves.isPersistent()) return false;
        if (leaves.getDistance() < 7) return false;

        onBlockRemove(block, getConfig().getLong("decay-delay"));
        if (getConfig().getBoolean("spawn-particles")) {
            block.getWorld()
                    .spawnParticle(Particle.BLOCK,
                            block.getLocation().add(0.5, 0.5, 0.5),
                            8, 0.2, 0.2, 0.2, 0,
                            block.getType().createBlockData());
        }
        if (getConfig().getBoolean("play-sound")) {
            block.getWorld().playSound(block.getLocation(),
                    Sound.BLOCK_GRASS_BREAK,
                    SoundCategory.BLOCKS, 0.05f, 1.2f);
        }
        block.breakNaturally();
        return true;
    }

    private void scheduleOneByOne(long delay) {
        if (!oneByOneScheduled.compareAndSet(false, true)) return;
        Scheduler.runLater(this::decayOne, Math.max(1L, delay));
    }

    private void decayOne() {
        oneByOneScheduled.set(false);

        Block block = scheduledBlocks.stream().findFirst().orElse(null);
        if (block == null) return;

        // The block belongs to a specific region, so the actual decay has to run there.
        Scheduler.runAt(block.getLocation(), () -> {
            decay(block);
            if (!scheduledBlocks.isEmpty()) {
                long delay = getConfig().getLong("decay-delay");
                scheduleOneByOne(delay <= 0 ? 1L : delay);
            }
        });
    }
}
