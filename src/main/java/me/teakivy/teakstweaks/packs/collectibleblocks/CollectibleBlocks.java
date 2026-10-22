package me.teakivy.teakstweaks.packs.collectibleblocks;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;

public class CollectibleBlocks extends BasePack {

    private final HashSet<Material> alwaysDropBlocks;
    private final HashSet<Material> silkTouchBlocks;
    private final boolean includeAlwaysDropBlocks;
    private final boolean includeSilkTouchBlocks;

    public CollectibleBlocks() {
        super(TTPack.COLLECTIBLE_BLOCKS, Material.ENDER_CHEST);

        includeAlwaysDropBlocks = getConfig().getBoolean("always-drop.enabled");
        alwaysDropBlocks = new HashSet<>();
        if (includeAlwaysDropBlocks) {
        for (String block : getConfig().getStringList("always-drop.blocks")) {
            Material item = Material.matchMaterial(block);
            if (item != null && item.isBlock()) {
                alwaysDropBlocks.add(item);
            }
        }
    }

        includeSilkTouchBlocks = getConfig().getBoolean("silk-touch-drop.enabled");
        silkTouchBlocks = new HashSet<>();
        if (includeSilkTouchBlocks) {
            for (String block : getConfig().getStringList("silk-touch-drop.blocks")) {
                Material item = Material.matchMaterial(block);
                if (item != null && item.isBlock()) {
                    silkTouchBlocks.add(item);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Material blockType = event.getBlock().getType();
        if (includeAlwaysDropBlocks && alwaysDropBlocks.contains(event.getBlock().getType())) {
            event.setCancelled(true);
            Block block = event.getBlock();
            Location location = block.getLocation().add(0.5, 0.5, 0.5);
            block.setType(Material.AIR);
            Scheduler.runAtLater(block.getLocation(), () -> block.getWorld().dropItemNaturally(location, new ItemStack(blockType)), 1L);
            return;
        }

        ItemStack handItem = event.getPlayer().getInventory().getItemInMainHand();
        if (includeSilkTouchBlocks && silkTouchBlocks.contains(event.getBlock().getType())) {
            if (handItem.containsEnchantment(Enchantment.SILK_TOUCH)) {
                event.setCancelled(true);
                Block block = event.getBlock();
                Location location = block.getLocation().add(0.5, 0.5, 0.5);
                block.setType(Material.AIR);
                Scheduler.runAtLater(block.getLocation(), () -> block.getWorld().dropItemNaturally(location, new ItemStack(blockType)), 1L);
            }
        }
    }
}
