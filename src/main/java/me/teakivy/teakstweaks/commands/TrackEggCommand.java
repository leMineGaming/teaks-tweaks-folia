package me.teakivy.teakstweaks.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.teakivy.teakstweaks.packs.dragoneggtracker.DragonEggTracker;
import me.teakivy.teakstweaks.utils.command.AbstractCommand;
import me.teakivy.teakstweaks.utils.permission.Permission;
import me.teakivy.teakstweaks.utils.register.TTCommand;
import org.bukkit.command.CommandSender;

public class TrackEggCommand extends AbstractCommand {

    public TrackEggCommand() {
        super(TTCommand.TRACKEGG, "dragon_egg_tracker");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> getCommand() {
        return Commands.literal("trackegg")
                .requires(perm(Permission.COMMAND_TRACKEGG))
                .executes(this::track)
                .build();
    }

    private int track(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        DragonEggTracker.scan(sender);
        return Command.SINGLE_SUCCESS;
    }
}
