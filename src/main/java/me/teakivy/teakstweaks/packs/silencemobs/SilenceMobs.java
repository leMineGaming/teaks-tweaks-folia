package me.teakivy.teakstweaks.packs.silencemobs;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.permission.Permission;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Objects;

public class SilenceMobs extends BasePack {

    public SilenceMobs() {
        super(TTPack.SILENCE_MOBS, Material.SCULK_SHRIEKER);

        if (minecartTypes.isEmpty()) {
            minecartTypes.add(EntityType.MINECART);
            minecartTypes.add(EntityType.FURNACE_MINECART);
            minecartTypes.add(EntityType.CHEST_MINECART);
            minecartTypes.add(EntityType.COMMAND_BLOCK_MINECART);
            minecartTypes.add(EntityType.TNT_MINECART);
            minecartTypes.add(EntityType.HOPPER_MINECART);
            minecartTypes.add(EntityType.SPAWNER_MINECART);
        }
    }

    ArrayList<EntityType> minecartTypes = new ArrayList<>();

    @EventHandler
    public void onSilence(PlayerInteractAtEntityEvent event) {
        if (!Permission.SILENCE_MOBS.check(event.getPlayer())) return;
        Entity entity = event.getRightClicked();

        if (minecartTypes.contains(entity.getType())) {
            if (!getConfig().getBoolean("allow-minecarts")) return;
            ItemStack nametag = event.getPlayer().getInventory().getItem(event.getHand());
            if (nametag.getType() != Material.NAME_TAG) return;
            if (!nametag.hasItemMeta()) return;
            if (!Objects.requireNonNull(nametag.getItemMeta()).getDisplayName()
                    .replaceAll("_", " ")
                    .replaceAll("-", " ")
                    .trim()
                    .equalsIgnoreCase(getString("activation_name")))
                return;
            entity.setSilent(true);
            entity.customName(getText("silenced_name"));
            event.setCancelled(true);

            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                event.getPlayer().getInventory().getItem(event.getHand()).setAmount(nametag.getAmount() - 1);
            }
            return;
        }

        try {
            Scheduler.runForLater(entity, () -> {
                if (!entity.isValid()) return;
                if (entity.customName() == null) return;
                if (entity.getCustomName().replaceAll("_", " ")
                        .replaceAll("-", " ")
                        .trim()
                        .equalsIgnoreCase(getString("activation_name"))) {
                    entity.setSilent(true);
                    entity.customName(getText("silenced_name"));
                }
            }, 10L);
        } catch (NoClassDefFoundError ignored) {
        }
    }

    @EventHandler
    public void onUnSilence(PlayerInteractAtEntityEvent event) {
        if (!Permission.SILENCE_MOBS.check(event.getPlayer())) return;
        Entity entity = event.getRightClicked();

        if (minecartTypes.contains(entity.getType())) {
            if (!getConfig().getBoolean("allow-minecarts")) return;
            ItemStack nametag = event.getPlayer().getInventory().getItem(event.getHand());
            if (nametag.getType() != Material.NAME_TAG) return;
            if (!nametag.hasItemMeta()) return;
            if (!Objects.requireNonNull(nametag.getItemMeta()).getDisplayName()
                    .replaceAll("_", " ")
                    .replaceAll("-", " ")
                    .trim()
                    .equalsIgnoreCase(getString("deactivation_name")))
                return;
            entity.setSilent(false);
            entity.customName(null);
            event.setCancelled(true);

            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                event.getPlayer().getInventory().getItem(event.getHand()).setAmount(nametag.getAmount() - 1);
            }
            return;
        }

        try {
            Scheduler.runForLater(entity, () -> {
                if (!entity.isValid()) return;
                if (entity.customName() == null) return;
                if (entity.getCustomName().replaceAll("_", " ")
                        .replaceAll("-", " ")
                        .trim()
                        .equalsIgnoreCase(getString("deactivation_name"))) {
                    entity.setSilent(false);
                    entity.customName(null);
                }
            }, 10L);
        } catch (NoClassDefFoundError ignored) {
        }
    }

}


