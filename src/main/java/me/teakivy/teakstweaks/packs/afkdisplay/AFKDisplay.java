package me.teakivy.teakstweaks.packs.afkdisplay;

import me.teakivy.teakstweaks.utils.lang.Translations;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.log.Logger;
import me.teakivy.teakstweaks.utils.config.Config;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AFKDisplay extends BasePack {

    public AFKDisplay() {
        super(TTPack.AFK_DISPLAY, Material.BELL);
    }

    @Override
    public void init() {
        super.init();
        register();
    }

    public static Map<UUID, Boolean> afk = new ConcurrentHashMap<>();
    static Map<UUID, Long> lastMove = new ConcurrentHashMap<>();

    static ScheduledTask afkTimer;

    static long afkMinutes;
    static long kickAfter;

    public void register() {
        afkMinutes = getConfig().getInt("afk-after");
        kickAfter = getConfig().getInt("kick-after");

        for (Player player : Bukkit.getOnlinePlayers()) {
            afk.putIfAbsent(player.getUniqueId(), false);
            lastMove.putIfAbsent(player.getUniqueId(), System.currentTimeMillis());
        }

        afkTimer = Scheduler.runTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                Boolean isAFK = afk.get(uuid);
                if (isAFK == null) continue;

                if (!isAFK) {
                    Long last = lastMove.get(uuid);
                    if (last != null && last + (afkMinutes * 60 * 1000) < System.currentTimeMillis()) {
                        Scheduler.runFor(player, () -> afk(player, false));
                    }
                    continue;
                }

                Long last = lastMove.get(uuid);
                if (last == null) continue;
                boolean shouldKick = kickAfter == 0
                        || last + (afkMinutes * 60 * 1000) + (kickAfter * 60 * 1000) < System.currentTimeMillis();
                if (shouldKick) {
                    Scheduler.runFor(player, () -> {
                        player.kick(newText(getConfig().getString("kick-message")));
                        AFKKickEvent afkKickEvent = new AFKKickEvent(player);
                        Bukkit.getPluginManager().callEvent(afkKickEvent);
                    });
                }
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        afk.put(player.getUniqueId(), false);
        lastMove.put(player.getUniqueId(), System.currentTimeMillis());
        AFKStatusChangeEvent afkEvent = new AFKStatusChangeEvent(player, false);
        Bukkit.getPluginManager().callEvent(afkEvent);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        unAFK(player);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (Boolean.TRUE.equals(afk.get(uuid))) {
            unAFK(player);
            return;
        }
        lastMove.put(uuid, System.currentTimeMillis());
    }

    @EventHandler
    public void chatEvent(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!Boolean.TRUE.equals(afk.get(player.getUniqueId()))) return;
        // Chat events are fired asynchronously, so move the entity work onto the player's region.
        Scheduler.runFor(player, () -> unAFK(player));
    }

    public static void afk(Player player, boolean fromCommand) {
        Long last = lastMove.get(player.getUniqueId());
        if (!fromCommand && last != null && last + (afkMinutes * 60 * 1000) > System.currentTimeMillis()) return;
        if (Boolean.TRUE.equals(afk.get(player.getUniqueId()))) return;

        afk.put(player.getUniqueId(), true);
        displayAFKMessage(player, true);

        // Folia does not support the main scoreboard API, so the AFK state is displayed through the
        // player list name instead of a scoreboard team.
        player.playerListName(displayName(player, true));

        AFKStatusChangeEvent afkEvent = new AFKStatusChangeEvent(player, true);
        Bukkit.getPluginManager().callEvent(afkEvent);
    }

    public static void unAFK(Player player) {
        if (!Boolean.TRUE.equals(afk.get(player.getUniqueId()))) return;

        afk.put(player.getUniqueId(), false);
        displayAFKMessage(player, false);
        player.playerListName(displayName(player, false));

        AFKStatusChangeEvent afkEvent = new AFKStatusChangeEvent(player, false);
        Bukkit.getPluginManager().callEvent(afkEvent);
    }

    private static Component displayName(Player player, boolean isAFK) {
        Component name = Component.text(player.getName());
        if (!isAFK) return name;

        String color = Config.getString("packs.afk-display.name-color");
        if (color != null && !color.equalsIgnoreCase("none")) {
            NamedTextColor textColor = NamedTextColor.NAMES.value(color);
            if (textColor != null) name = name.color(textColor);
        }

        if (Config.getBoolean("packs.afk-display.display-badge")) {
            String badge = Config.getString("packs.afk-display.badge");
            if (badge != null) name = newText(badge).append(name);
        }

        return name;
    }

    public static void displayAFKMessage(Player player, Boolean isAFK) {
        if (isAFK) {
            if (Config.getBoolean("packs.afk-display.message.display-to-self")) {
                player.sendMessage(Translations.get("afk_display.self_now_afk"));
            }
            if (Config.getBoolean("packs.afk-display.message.display-to-everyone")) {
                for (Player player1 : Bukkit.getOnlinePlayers()) {
                    if (player1.getUniqueId() != player.getUniqueId()) {
                        player1.sendMessage(Translations.get("afk_display.other_now_afk", insert("player", player.getName())));
                    }
                }
            }
            if (Config.getBoolean("packs.afk-display.message.display-to-console")) {
                Logger.info(Translations.get("afk_display.other_now_afk", insert("player", player.getName())));
            }
        } else {
            if (Config.getBoolean("packs.afk-display.message.display-to-self")) {
                player.sendMessage(Translations.get("afk_display.self_not_afk"));
            }
            if (Config.getBoolean("packs.afk-display.message.display-to-everyone")) {
                for (Player player1 : Bukkit.getOnlinePlayers()) {
                    if (player1.getUniqueId() != player.getUniqueId()) {
                        player1.sendMessage(Translations.get("afk_display.other_not_afk", insert("player", player.getName())));
                    }
                }
            }
            if (Config.getBoolean("packs.afk-display.message.display-to-console")) {
                Logger.info(Translations.get("afk_display.other_not_afk", insert("player", player.getName())));
            }
        }
    }

    @Override
    public void unregister() {
        super.unregister();
        Scheduler.cancel(afkTimer);
        afkTimer = null;
    }

    public static void uninstall() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(Component.text(player.getName()));
        }
    }

    public static boolean isAFK(Player player) {
        return afk.containsKey(player.getUniqueId()) && afk.get(player.getUniqueId());
    }


}
