package me.teakivy.teakstweaks.packs.dragoneggtracker;

import me.teakivy.teakstweaks.utils.log.Logger;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;

import java.util.UUID;

/**
 * Isolates all LuckPerms API access so the rest of the plugin can be loaded even when LuckPerms is
 * not installed.
 */
final class LuckPermsHook {

    private final String groupName;
    private volatile boolean ready;

    LuckPermsHook(String groupName) {
        this.groupName = groupName;
    }

    /**
     * Make sure the configured group exists. The group is created when {@code create} is true.
     */
    void ensureGroup(boolean create) {
        LuckPerms luckPerms = luckPerms();
        if (luckPerms == null) return;

        GroupManager groups = luckPerms.getGroupManager();
        if (groups.getGroup(groupName) != null) {
            ready = true;
            return;
        }

        if (!create) {
            Logger.warning(Component.text("The LuckPerms group '" + groupName + "' does not exist. Create it, or set create-group to true in the dragon-egg-tracker config."));
            return;
        }

        try {
            groups.createAndLoadGroup(groupName)
                    .thenCompose(groups::saveGroup)
                    .thenRun(() -> {
                        ready = true;
                        Logger.info(Component.text("Created LuckPerms group '" + groupName + "' for the dragon egg tracker."));
                    })
                    .exceptionally(exception -> {
                        Logger.warning(Component.text("Failed to create the LuckPerms group '" + groupName + "': " + exception.getMessage()));
                        return null;
                    });
        } catch (RuntimeException exception) {
            Logger.warning(Component.text("Failed to create the LuckPerms group '" + groupName + "': " + exception.getMessage()));
        }
    }

    boolean isReady() {
        return ready;
    }

    /**
     * Add or remove the parent group node for a user.
     *
     * @return true if the update was scheduled, false if LuckPerms is not ready
     */
    boolean apply(UUID uuid, boolean give) {
        if (!ready) return false;

        LuckPerms luckPerms = luckPerms();
        if (luckPerms == null) return false;

        try {
            Node node = Node.builder("group." + groupName).build();
            luckPerms.getUserManager().modifyUser(uuid, user -> {
                if (give) {
                    user.data().add(node);
                } else {
                    user.data().remove(node);
                }
            }).exceptionally(exception -> {
                Logger.warning(Component.text("Failed to update the LuckPerms group for " + uuid + ": " + exception.getMessage()));
                return null;
            });
            return true;
        } catch (RuntimeException exception) {
            Logger.warning(Component.text("Failed to update the LuckPerms group for " + uuid + ": " + exception.getMessage()));
            return false;
        }
    }

    private LuckPerms luckPerms() {
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException | NoClassDefFoundError exception) {
            return null;
        }
    }
}
