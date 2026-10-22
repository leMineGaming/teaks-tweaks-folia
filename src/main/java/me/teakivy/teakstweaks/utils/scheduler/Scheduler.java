package me.teakivy.teakstweaks.utils.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.teakivy.teakstweaks.TeaksTweaks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A small wrapper around Folia's schedulers.
 * <p>
 * Folia replaces the classic {@link org.bukkit.scheduler.BukkitScheduler} with four different
 * schedulers (global region, region, entity and async). This class exposes them through a single
 * place so every task in the plugin is scheduled on the thread that actually owns the data it
 * touches.
 */
public final class Scheduler {

    private static final long MILLIS_PER_TICK = 50L;

    private Scheduler() {
    }

    private static Plugin plugin() {
        return TeaksTweaks.getInstance();
    }

    /**
     * Run a task on the global region thread.
     */
    public static void run(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin(), task);
    }

    /**
     * Run a task on the global region thread after a delay.
     *
     * @param delayTicks delay in ticks, values lower than 1 are treated as 1
     */
    public static ScheduledTask runLater(Runnable task, long delayTicks) {
        if (delayTicks <= 0) {
            run(task);
            return null;
        }
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin(), ignored -> task.run(), delayTicks);
    }

    /**
     * Run a repeating task on the global region thread.
     */
    public static ScheduledTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return runTimer(ignored -> task.run(), delayTicks, periodTicks);
    }

    /**
     * Run a repeating task on the global region thread, giving the task access to itself so it can
     * cancel.
     */
    public static ScheduledTask runTimer(Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin(),
                task,
                Math.max(1L, delayTicks),
                Math.max(1L, periodTicks));
    }

    /**
     * Run a task on the async scheduler.
     */
    public static ScheduledTask runAsync(Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(plugin(), ignored -> task.run());
    }

    /**
     * Run a task on the async scheduler after a delay.
     *
     * @param delayTicks delay in ticks
     */
    public static ScheduledTask runAsyncLater(Runnable task, long delayTicks) {
        return Bukkit.getAsyncScheduler().runDelayed(
                plugin(),
                ignored -> task.run(),
                Math.max(0L, delayTicks) * MILLIS_PER_TICK,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Run a task on the region that owns a location.
     */
    public static void runAt(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) return;
        Bukkit.getRegionScheduler().execute(plugin(), location, task);
    }

    /**
     * Run a task on the region that owns a location after a delay.
     */
    public static ScheduledTask runAtLater(Location location, Runnable task, long delayTicks) {
        if (location == null || location.getWorld() == null) return null;
        if (delayTicks <= 0) {
            runAt(location, task);
            return null;
        }
        return Bukkit.getRegionScheduler().runDelayed(plugin(), location, ignored -> task.run(), delayTicks);
    }

    /**
     * Run a repeating task on the region that owns a location.
     */
    public static ScheduledTask runAtTimer(Location location, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null) return null;
        return Bukkit.getRegionScheduler().runAtFixedRate(
                plugin(),
                location,
                task,
                Math.max(1L, delayTicks),
                Math.max(1L, periodTicks));
    }

    /**
     * Run a task on the region that currently owns an entity.
     */
    public static ScheduledTask runFor(Entity entity, Runnable task) {
        return runFor(entity, task, null);
    }

    /**
     * Run a task on the region that currently owns an entity.
     *
     * @param retired run if the entity is removed before the task executes
     */
    public static ScheduledTask runFor(Entity entity, Runnable task, Runnable retired) {
        if (entity == null) {
            if (retired != null) retired.run();
            return null;
        }
        return entity.getScheduler().run(plugin(), ignored -> task.run(), retired);
    }

    /**
     * Run a task on the region that owns an entity after a delay. The entity scheduler follows the
     * entity if it moves to another region in the meantime.
     */
    public static ScheduledTask runForLater(Entity entity, Runnable task, long delayTicks) {
        if (entity == null) return null;
        if (delayTicks <= 0) {
            return runFor(entity, task);
        }
        return entity.getScheduler().runDelayed(plugin(), ignored -> task.run(), null, delayTicks);
    }

    /**
     * Run a repeating task on the region that owns an entity.
     */
    public static ScheduledTask runForTimer(Entity entity, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        if (entity == null) return null;
        return entity.getScheduler().runAtFixedRate(
                plugin(),
                task,
                null,
                Math.max(1L, delayTicks),
                Math.max(1L, periodTicks));
    }

    /**
     * Cancel a task if it is not null.
     */
    public static void cancel(ScheduledTask task) {
        if (task != null) task.cancel();
    }
}
