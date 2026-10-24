package me.teakivy.teakstweaks.packs.noelytra;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.permission.Permission;
import me.teakivy.teakstweaks.utils.register.TTPack;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class NoElytra extends BasePack {

    public NoElytra() {
        super(TTPack.NO_ELYTRA, Material.ELYTRA);
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!Permission.NO_ELYTRA.check(player)) return;

        event.setCancelled(true);
        player.sendActionBar(getText("forbidden"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding()) return;
        if (!Permission.NO_ELYTRA.check(player)) return;

        player.setGliding(false);
    }
}
