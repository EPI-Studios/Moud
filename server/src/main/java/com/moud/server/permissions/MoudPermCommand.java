package com.moud.server.permissions;

import com.moud.server.network.ServerNetworkManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

public final class MoudPermCommand extends Command {
    public MoudPermCommand() {
        super("moudperm", "moudperms");

        ArgumentLiteral grantLiteral = ArgumentType.Literal("grant");
        ArgumentLiteral revokeLiteral = ArgumentType.Literal("revoke");
        ArgumentLiteral showLiteral = ArgumentType.Literal("show");
        ArgumentLiteral listLiteral = ArgumentType.Literal("list");

        ArgumentString targetArg = ArgumentType.String("player");
        ArgumentString permArg = ArgumentType.String("permission");

        setDefaultExecutor((sender, context) -> sender.sendMessage(Component.text(
                "Usage: /moudperm <grant|revoke|show|list> ...",
                NamedTextColor.RED
        )));

        addSyntax((sender, context) -> {
            if (!PermissionCommandUtil.ensureConsole(sender)) {
                return;
            }
            PermissionCommandUtil.ResolvedTarget target = PermissionCommandUtil.resolveTarget(sender, context.get(targetArg));
            if (target == null) {
                return;
            }
            ServerPermission permission = PermissionCommandUtil.parsePermission(sender, context.get(permArg));
            if (permission == null) {
                return;
            }
            PermissionManager.getInstance().grant(target.uuid(), permission, target.name());
            if (target.onlinePlayer() != null && ServerNetworkManager.getInstance() != null) {
                ServerNetworkManager.getInstance().syncPermissionState(target.onlinePlayer());
            }
            sender.sendMessage(Component.text(
                    "Granted " + permission.name() + " to " + formatTarget(target),
                    NamedTextColor.GREEN
            ));
        }, grantLiteral, targetArg, permArg);

        addSyntax((sender, context) -> {
            if (!PermissionCommandUtil.ensureConsole(sender)) {
                return;
            }
            PermissionCommandUtil.ResolvedTarget target = PermissionCommandUtil.resolveTarget(sender, context.get(targetArg));
            if (target == null) {
                return;
            }
            ServerPermission permission = PermissionCommandUtil.parsePermission(sender, context.get(permArg));
            if (permission == null) {
                return;
            }
            PermissionManager.getInstance().revoke(target.uuid(), permission);
            if (target.onlinePlayer() != null && ServerNetworkManager.getInstance() != null) {
                ServerNetworkManager.getInstance().syncPermissionState(target.onlinePlayer());
            }
            sender.sendMessage(Component.text(
                    "Revoked " + permission.name() + " from " + formatTarget(target),
                    NamedTextColor.YELLOW
            ));
        }, revokeLiteral, targetArg, permArg);

        addSyntax((sender, context) -> {
            if (!PermissionCommandUtil.ensureConsole(sender)) {
                return;
            }
            PermissionCommandUtil.ResolvedTarget target = PermissionCommandUtil.resolveTarget(sender, context.get(targetArg));
            if (target == null) {
                return;
            }
            EnumSet<ServerPermission> direct = PermissionManager.getInstance().getDirectPermissions(target.uuid());
            sender.sendMessage(Component.text(
                    "Permissions for " + formatTarget(target) + ": " + (direct.isEmpty() ? "<none>" : direct),
                    NamedTextColor.GRAY
            ));
        }, showLiteral, targetArg);

        addSyntax((sender, context) -> {
            if (!PermissionCommandUtil.ensureConsole(sender)) {
                return;
            }
            Map<UUID, EnumSet<ServerPermission>> snapshot = PermissionManager.getInstance().snapshot();
            if (snapshot.isEmpty()) {
                sender.sendMessage(Component.text("No permission entries.", NamedTextColor.GRAY));
                return;
            }
            sender.sendMessage(Component.text("Permission entries:", NamedTextColor.GRAY));
            snapshot.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().toString()))
                    .forEach(entry -> sender.sendMessage(Component.text(
                            "- " + entry.getKey() + " " + entry.getValue(),
                            NamedTextColor.GRAY
                    )));
        }, listLiteral);
    }

    private String formatTarget(PermissionCommandUtil.ResolvedTarget target) {
        String name = target.name();
        if (name == null || name.isBlank()) {
            return target.uuid().toString();
        }
        return name + " (" + target.uuid() + ")";
    }
}
