package com.moud.server.dev;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;

public final class GamemodeCommand extends Command {

    public GamemodeCommand() {
        super("gamemode", "gm");

        ArgumentEnum<GameMode> gameModeArg = ArgumentType.Enum("mode", GameMode.class);

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /gamemode <survival|creative|adventure|spectator>", NamedTextColor.RED));
        });

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED));
                return;
            }

            GameMode mode = context.get(gameModeArg);
            player.setGameMode(mode);

            sender.sendMessage(Component.text("Set own game mode to ", NamedTextColor.GRAY)
                    .append(Component.text(mode.name().toLowerCase(), NamedTextColor.AQUA)));
        }, gameModeArg);
    }
}
