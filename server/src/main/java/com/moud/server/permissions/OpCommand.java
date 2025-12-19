package com.moud.server.permissions;

import com.moud.server.network.ServerNetworkManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class OpCommand extends Command {
    public OpCommand() {
        super("op");

        ArgumentString targetArg = ArgumentType.String("player");

        setDefaultExecutor((sender, context) ->
                sender.sendMessage(Component.text("Usage: /op <player|uuid>", NamedTextColor.RED)));

        addSyntax((sender, context) -> {
            if (!PermissionCommandUtil.ensureConsole(sender)) {
                return;
            }
            PermissionCommandUtil.ResolvedTarget target = PermissionCommandUtil.resolveTarget(sender, context.get(targetArg));
            if (target == null) {
                return;
            }
            PermissionManager.getInstance().grant(target.uuid(), ServerPermission.OP, target.name());
            if (target.onlinePlayer() != null && ServerNetworkManager.getInstance() != null) {
                ServerNetworkManager.getInstance().syncPermissionState(target.onlinePlayer());
            }
            sender.sendMessage(Component.text("Granted OP to " + formatTarget(target), NamedTextColor.GREEN));
        }, targetArg);
    }

    private String formatTarget(PermissionCommandUtil.ResolvedTarget target) {
        String name = target.name();
        if (name == null || name.isBlank()) {
            return target.uuid().toString();
        }
        return name + " (" + target.uuid() + ")";
    }
}
