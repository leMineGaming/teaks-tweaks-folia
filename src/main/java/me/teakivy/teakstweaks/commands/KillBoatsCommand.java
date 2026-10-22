package me.teakivy.teakstweaks.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.teakivy.teakstweaks.utils.command.AbstractCommand;
import me.teakivy.teakstweaks.utils.permission.Permission;
import me.teakivy.teakstweaks.utils.register.TTCommand;
import me.teakivy.teakstweaks.utils.scheduler.RegionScanner;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class KillBoatsCommand extends AbstractCommand {

    public KillBoatsCommand() {
        super(TTCommand.KILLBOATS, "killboats");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> getCommand() {
        return Commands.literal("killboats")
                .requires(perm(Permission.COMMAND_KILLBOATS))
                .executes(this::killboats)
                .build();
    }

    private int killboats(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        List<EntityType> boatTypes = new ArrayList<>();
        for (EntityType value : EntityType.values()) {
            if (value.name().contains("BOAT") || value.name().contains("RAFT")) {
                boatTypes.add(value);
            }
        }
        List<World> worlds = Bukkit.getWorlds();
        java.util.concurrent.atomic.AtomicInteger boats = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(worlds.size());
        for (World world : worlds) {
            RegionScanner.forEachEntity(world, entity -> {
                if (!boatTypes.contains(entity.getType())) return;
                if (!entity.getPassengers().isEmpty()) return;

                entity.remove();
                boats.incrementAndGet();
            }, () -> {
                if (remaining.decrementAndGet() == 0) {
                    sender.sendMessage(getText("removed_boats", insert("count", boats.get())));
                }
            });
        }

        return Command.SINGLE_SUCCESS;
    }
}
