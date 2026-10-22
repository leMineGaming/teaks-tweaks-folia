package me.teakivy.teakstweaks.packs.spawningspheres;

import me.teakivy.teakstweaks.packs.BasePack;
import me.teakivy.teakstweaks.utils.JsonManager;
import me.teakivy.teakstweaks.utils.register.TTPack;
import me.teakivy.teakstweaks.utils.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;

public class SpawningSpheres extends BasePack {
    private static SpawningSphere redSphere;
    private static SpawningSphere blueSphere;
    private static SpawningSphere greenSphere;

    public SpawningSpheres() {
        super(TTPack.SPAWNING_SPHERES, Material.WARDEN_SPAWN_EGG);
    }

    @Override
    public void init() {
        super.init();

        SphereType.initTeams();
        LinkedHashMap<String, Object> json = getJson();

        redSphere = new SpawningSphere(json, SphereType.RED);
        blueSphere = new SpawningSphere(json, SphereType.BLUE);
        greenSphere = new SpawningSphere(json, SphereType.GREEN);
    }

    public static synchronized void save() {
        LinkedHashMap<String, Object> json = new LinkedHashMap<>();
        json.putAll(redSphere.serialize());
        json.putAll(blueSphere.serialize());
        json.putAll(greenSphere.serialize());

        JsonManager.saveToFile(json, "data/spawning_spheres.json");
    }

    public static synchronized boolean teleport(SphereType type, Player player) {
        SpawningSphere sphere = getSphere(type);

        if (sphere == null) return false;
        if (!sphere.isInUse()) return false;

        sphere.teleport(player);
        return true;
    }

    public static synchronized boolean summonSphere(SphereType type, Location location) {
        SpawningSphere sphere = getSphere(type);

        if (sphere == null) return false;
        if (sphere.isInUse()) return false;

        sphere.setCenter(location);
        sphere.createSphere();
        save();
        return true;
    }

    public static synchronized boolean removeSphere(SphereType type, Player executor) {
        SpawningSphere sphere = getSphere(type);

        if (sphere == null) return false;
        if (!sphere.isInUse()) return false;

        Location location = executor.getLocation();

        executor.teleportAsync(sphere.getCenter());

        Scheduler.runAtLater(sphere.getCenter(), sphere::removeSphere, 20L);
        Scheduler.runForLater(executor, () -> {
            executor.teleportAsync(location);
            save();
        }, 20L * 3);
        return true;
    }

    public static SpawningSphere getSphere(SphereType type) {
        return switch (type) {
            case RED -> redSphere;
            case BLUE -> blueSphere;
            case GREEN -> greenSphere;
        };
    }

    private static LinkedHashMap<String, Object> getJson() {
        return JsonManager.get("data/spawning_spheres.json");
    }
}
