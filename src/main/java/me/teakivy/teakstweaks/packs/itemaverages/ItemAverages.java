package me.teakivy.teakstweaks.packs.itemaverages;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.log.Logger;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ItemAverages extends BasePack {
    public static volatile boolean inUse = false;
    public static volatile Location tracking = null;
    private static volatile boolean shouldStop = false;


    static List<Entity> itemsTracked = new CopyOnWriteArrayList<>();
    static Map<Material, Integer> items = new ConcurrentHashMap<>();

    public ItemAverages() {
        super(TTPack.ITEM_AVERAGES, Material.HOPPER);
    }

    public static void spawnTracker(Location loc, Player player) {
        glowingBlock(loc, 2*60*20);
        startTracker(player);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains("tracker")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity().getScoreboardTags().contains("tracker")) {
            event.getDrops().clear();
        }
    }

    public static void startTracker(Player player) {
        trackItems(player);
    }

    public static void trackItems(Player player) {
        Location trackLocation = tracking;
        if (trackLocation == null) return;

        // Nearby entities are region limited on Folia, and this tracker only cares about items on
        // the block it was created on, so this is exactly the data we need.
        for (Entity entity : trackLocation.getWorld().getNearbyEntities(trackLocation, 2, 2, 2)) {
            if (entity.getType() == EntityType.ITEM) {
                if (entity.getLocation().getBlock().getLocation().distanceSquared(trackLocation) < 1 && !itemsTracked.contains(entity)) {
                    if (!(entity instanceof Item)) continue;
                    ItemStack item = ((Item) entity).getItemStack();
                    itemsTracked.add(entity);
                    if (!items.containsKey(item.getType())) {
                        items.put(item.getType(), item.getAmount());
                    } else {
                        items.put(item.getType(), items.get(item.getType()) + item.getAmount());
                    }
                }
            }
        }
        if (!shouldStop) {
            Scheduler.runAtLater(trackLocation, () -> trackItems(player), 1L);
            return;
        }
        shouldStop = false;
        inUse = false;
        tracking = null;

        sendTotaledMessage(player, items);

        itemsTracked = new CopyOnWriteArrayList<>();
        items = new ConcurrentHashMap<>();
    }

    private static void sendTotaledMessage(Player player, Map<Material, Integer> items) {
        Map<Material, Integer> totalItems = new java.util.HashMap<>();

        items.forEach((item, amount) -> {
            totalItems.put(item, amount * 30);
        });

        if (!totalItems.isEmpty()) {
            player.sendMessage(newText("<dark_gray>-------------------------"));
            player.sendMessage(Component.translatable("item_averages.source_production"));
            totalItems.forEach((item, amount) -> {
                player.sendMessage(
                        Component.translatable("item_averages.item",
                                insert("amount", amount),
                                insert("item", item.toString().toLowerCase().replace("_", " "))));
            });
            player.sendMessage(newText("<dark_gray>-------------------------"));
        } else {
            player.sendMessage(newText("<dark_gray>-------------------------"));
            player.sendMessage(Component.translatable("item_averages.no_items"));
            player.sendMessage(newText("<dark_gray>-------------------------"));
        }
    }

    public static void glowingBlock(Location loc, int glowLength) {
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc.getBlock().getLocation().add(.5, 0, .5), EntityType.MAGMA_CUBE);
        MagmaCube cube = (MagmaCube) entity;
        cube.setSize(2);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, glowLength, 0, true, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowLength, 0, true, false));
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.addScoreboardTag("tracker");
        inUse = true;
        tracking = loc.getBlock().getLocation();


        Scheduler.runForLater(entity, () -> {
            entity.remove();
            Logger.info(Component.translatable("item_averages.log_finish"));
            shouldStop = true;
        }, glowLength);
    }
}
