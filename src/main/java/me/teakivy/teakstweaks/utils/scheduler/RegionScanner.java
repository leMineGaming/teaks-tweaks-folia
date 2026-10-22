package me.teakivy.teakstweaks.utils.scheduler;

import org.bukkit.Location;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Utilities for iterating entities that live in other regions.
 * <p>
 * Folia does not allow a plugin to freely read entities that belong to another region, so instead
 * of {@link World#getEntities()} the chunks are visited one by one and the entity access happens on
 * the region thread that owns each chunk.
 */
public final class RegionScanner {

    private RegionScanner() {
    }

    /**
     * Visit every entity in the loaded chunks of a world.
     *
     * @param world      the world to scan
     * @param action     the action to run on the region owning the entity
     * @param completion run on the global region thread once every chunk has been visited
     */
    public static void forEachEntity(World world, Consumer<Entity> action, Runnable completion) {
        forEachEntity(world, null, -1, action, completion);
    }

    /**
     * Visit every entity within a radius of a location, using the loaded chunks of the world.
     *
     * @param world      the world to scan
     * @param center     the center of the search
     * @param radius     the radius, in blocks
     * @param action     the action to run on the region owning the entity
     * @param completion run on the global region thread once every chunk has been visited
     */
    public static void forEachEntity(World world, Location center, double radius, Consumer<Entity> action, Runnable completion) {
        if (world == null) {
            runCompletion(completion);
            return;
        }

        org.bukkit.Chunk[] chunks = world.getLoadedChunks();
        if (chunks.length == 0) {
            runCompletion(completion);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(chunks.length);
        for (org.bukkit.Chunk chunk : chunks) {
            if (center != null && radius > 0 && !isChunkInRange(chunk, center, radius)) {
                if (remaining.decrementAndGet() == 0) runCompletion(completion);
                continue;
            }

            Location chunkLocation = new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4);
            Scheduler.runAt(chunkLocation, () -> {
                try {
                    for (Entity entity : chunk.getEntities()) {
                        if (center != null && radius > 0) {
                            if (entity.getWorld() != center.getWorld()) continue;
                            if (entity.getLocation().distanceSquared(center) > radius * radius) continue;
                        }
                        action.accept(entity);
                    }
                } catch (IllegalStateException ignored) {
                    // The chunk was unloaded before the region task ran.
                } finally {
                    if (remaining.decrementAndGet() == 0) runCompletion(completion);
                }
            });
        }
    }

    /**
     * Visit every block state (tile entity) in the loaded chunks of a world.
     *
     * @param world      the world to scan
     * @param action     the action to run on the region owning the chunk
     * @param completion run on the global region thread once every chunk has been visited
     */
    public static void forEachBlockState(World world, Consumer<BlockState> action, Runnable completion) {
        if (world == null) {
            runCompletion(completion);
            return;
        }

        org.bukkit.Chunk[] chunks = world.getLoadedChunks();
        if (chunks.length == 0) {
            runCompletion(completion);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(chunks.length);
        for (org.bukkit.Chunk chunk : chunks) {
            Location chunkLocation = new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4);
            Scheduler.runAt(chunkLocation, () -> {
                try {
                    for (BlockState state : chunk.getTileEntities()) {
                        action.accept(state);
                    }
                } catch (IllegalStateException ignored) {
                    // The chunk was unloaded before the region task ran.
                } finally {
                    if (remaining.decrementAndGet() == 0) runCompletion(completion);
                }
            });
        }
    }

    /**
     * Scan the loaded chunks around a location for a specific block type.
     * <p>
     * Empty sections are skipped, which keeps the scan reasonably cheap. The action runs on the
     * region that owns each chunk.
     *
     * @param world         the world to scan
     * @param center        the center of the search
     * @param radiusChunks  the radius, in chunks
     * @param material      the block type to look for
     * @param action        the action to run for every matching block
     * @param completion    run on the global region thread once every chunk has been scanned
     */
    public static void forEachBlock(World world, Location center, int radiusChunks, Material material,
                                    Consumer<Location> action, Runnable completion) {
        if (world == null || center == null || center.getWorld() == null) {
            runCompletion(completion);
            return;
        }

        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        int radius = Math.max(0, radiusChunks);

        java.util.List<org.bukkit.Chunk> chunks = new java.util.ArrayList<>();
        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            if (Math.abs(chunk.getX() - centerChunkX) > radius) continue;
            if (Math.abs(chunk.getZ() - centerChunkZ) > radius) continue;
            chunks.add(chunk);
        }

        if (chunks.isEmpty()) {
            runCompletion(completion);
            return;
        }

        AtomicInteger remaining = new AtomicInteger(chunks.size());
        int minSection = world.getMinHeight() >> 4;
        int maxSection = (world.getMaxHeight() - 1) >> 4;
        int sectionCount = maxSection - minSection + 1;

        for (org.bukkit.Chunk chunk : chunks) {
            Location chunkLocation = new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4);
            Scheduler.runAt(chunkLocation, () -> {
                try {
                    ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
                    for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
                        if (snapshot.isSectionEmpty(sectionIndex)) continue;

                        int sectionY = minSection + sectionIndex;
                        int startY = Math.max(world.getMinHeight(), sectionY << 4);
                        int endY = Math.min(world.getMaxHeight() - 1, (sectionY << 4) + 15);

                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                for (int y = startY; y <= endY; y++) {
                                    if (snapshot.getBlockType(x, y, z) != material) continue;
                                    action.accept(new Location(world,
                                            (chunk.getX() << 4) + x,
                                            y,
                                            (chunk.getZ() << 4) + z));
                                }
                            }
                        }
                    }
                } catch (RuntimeException ignored) {
                    // The chunk was unloaded before the region task ran.
                } finally {
                    if (remaining.decrementAndGet() == 0) runCompletion(completion);
                }
            });
        }
    }

    private static boolean isChunkInRange(org.bukkit.Chunk chunk, Location center, double radius) {
        if (center.getWorld() == null || !chunk.getWorld().equals(center.getWorld())) return false;

        double chunkCenterX = (chunk.getX() << 4) + 8;
        double chunkCenterZ = (chunk.getZ() << 4) + 8;
        double maxDistance = radius + 16;
        return Math.abs(chunkCenterX - center.getX()) <= maxDistance
                && Math.abs(chunkCenterZ - center.getZ()) <= maxDistance;
    }

    private static void runCompletion(Runnable completion) {
        if (completion == null) return;
        Scheduler.run(completion);
    }
}
