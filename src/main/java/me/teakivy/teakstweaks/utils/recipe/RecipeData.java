package me.teakivy.teakstweaks.utils.recipe;

import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;

public class RecipeData {
    private final String parent;
    private final NamespacedKey key;
    private final Recipe recipe;

    public RecipeData(String parent, Recipe recipe) {
        this.parent = parent;
        this.recipe = recipe;
        this.key = ((Keyed) recipe).getKey();
    }

    public String getParent() {
        return parent;
    }

    public NamespacedKey getKey() {
        return key;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public void grantRecipe() {
        register();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            grantRecipe(player);
        }
    }

    public void grantRecipe(Player player) {
        register();
        // Discovering a recipe modifies player data, which belongs to the player's region.
        Scheduler.runFor(player, () -> {
            if (!player.isOnline()) return;
            if (player.hasDiscoveredRecipe(key)) return;
            player.discoverRecipe(key);
        });
    }

    public void revokeRecipe() {
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            revokeRecipe(player);
        }
    }

    public void revokeRecipe(Player player) {
        Scheduler.runFor(player, () -> {
            if (!player.isOnline()) return;
            if (!player.hasDiscoveredRecipe(key)) return;
            player.undiscoverRecipe(key);
        });
    }

    public void register() {
        if (Bukkit.getRecipe(key) != null) return;
        Bukkit.addRecipe(recipe);
    }

    public void unregister() {
        Bukkit.removeRecipe(key);
        revokeRecipe();
    }
}
