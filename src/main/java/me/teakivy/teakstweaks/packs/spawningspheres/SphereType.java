package me.teakivy.teakstweaks.packs.spawningspheres;

import me.teakivy.teakstweaks.utils.lang.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import me.teakivy.teakstweaks.utils.log.Logger;
import net.kyori.adventure.text.Component;

public enum SphereType {
    RED,
    BLUE,
    GREEN;

    /**
     * Gets the color name of the sphere
     * @return the color name of the sphere (red, blue, green)
     */
    public String getColorName() {
        return switch (this) {
            case RED -> "red";
            case BLUE -> "blue";
            case GREEN -> "green";
        };
    }

    /**
     * Gets the main material of the sphere
     * @return the main material of the sphere
     */
    public Material getMaterial() {
        return switch (this) {
            case RED -> Material.RED_CONCRETE;
            case BLUE -> Material.BLUE_CONCRETE;
            case GREEN -> Material.GREEN_CONCRETE;
        };
    }

    /**
     * Gets the sub material of the sphere
     * @return the sub material of the sphere
     */
    public Material getSubMaterial() {
        return switch (this) {
            case RED -> Material.ORANGE_CONCRETE;
            case BLUE -> Material.CYAN_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
        };
    }

    /**
     * Gets the chat color of the sphere
     * @return the chat color of the sphere
     */
    public TextColor getColor() {
        return switch (this) {
            case RED -> TextColor.color(0xFF5555);
            case BLUE -> TextColor.color(0x5555FF);
            case GREEN -> TextColor.color(0x55FF55);
        };
    }

    public Component getName() {
        Component name = switch (this) {
            case RED -> Translations.get("spawning_spheres.sphere.red");
            case BLUE -> Translations.get("spawning_spheres.sphere.blue");
            case GREEN -> Translations.get("spawning_spheres.sphere.green");
        };

        return name.color(getColor());
    }

    private static boolean teamsSupported = true;

    public static void initTeams() {
        try {
            for (SphereType type : values()) {
                type.createTeam();
            }
        } catch (UnsupportedOperationException exception) {
            teamsSupported = false;
            Logger.warning(Component.text("Spawning Spheres cannot color the sphere markers because Folia does not support the scoreboard API. The spheres will still work, but without their team colors."));
        }
    }

    private Team createTeam() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = "sphere_" + getColorName();
        Team team = sb.getTeam(name);
        if (team != null) return team;

        team = sb.registerNewTeam(name);
        team.setColor(switch (this) {
            case RED -> ChatColor.RED;
            case BLUE -> ChatColor.BLUE;
            case GREEN -> ChatColor.GREEN;
        });
        return team;
    }

    /**
     * Get the scoreboard team used to color this sphere.
     *
     * @return the team, or null when the server does not support the scoreboard API (Folia)
     */
    public Team getTeam() {
        if (!teamsSupported) return null;
        try {
            return createTeam();
        } catch (UnsupportedOperationException exception) {
            teamsSupported = false;
            return null;
        }
    }

    public static SphereType getSphereType(String name) {
        return switch (name.toLowerCase()) {
            case "red" -> RED;
            case "blue" -> BLUE;
            case "green" -> GREEN;
            default -> null;
        };
    }
}
