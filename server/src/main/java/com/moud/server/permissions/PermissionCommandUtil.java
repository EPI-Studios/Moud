package com.moud.server.permissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

final class PermissionCommandUtil {
    private PermissionCommandUtil() {
    }

    static boolean ensureConsole(CommandSender sender) {
        if (sender instanceof Player) {
            sender.sendMessage(Component.text("This command can only be used from the server console.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    static @Nullable ResolvedTarget resolveTarget(CommandSender sender, String token) {
        if (token == null || token.isBlank()) {
            sender.sendMessage(Component.text("Provide a player name or UUID.", NamedTextColor.RED));
            return null;
        }
        String trimmed = token.trim();

        Player byName = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(p -> p.getUsername().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
        if (byName != null) {
            return new ResolvedTarget(byName.getUuid(), byName.getUsername(), byName);
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Player '" + trimmed + "' is not online; provide UUID instead.", NamedTextColor.RED));
            return null;
        }

        Player online = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(p -> p.getUuid().equals(uuid))
                .findFirst()
                .orElse(null);
        String name = online != null ? online.getUsername() : null;
        return new ResolvedTarget(uuid, name, online);
    }

    static @Nullable ServerPermission parsePermission(CommandSender sender, String token) {
        if (token == null || token.isBlank()) {
            sender.sendMessage(Component.text("Provide a permission name.", NamedTextColor.RED));
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "op", "admin" -> ServerPermission.OP;
            case "editor" -> ServerPermission.EDITOR;
            case "dev", "devutils", "dev_utils", "dev-utils" -> ServerPermission.DEV_UTILS;
            default -> {
                sender.sendMessage(Component.text("Unknown permission '" + token + "'. Use: op, editor, dev_utils", NamedTextColor.RED));
                yield null;
            }
        };
    }

    record ResolvedTarget(UUID uuid, @Nullable String name, @Nullable Player onlinePlayer) {
    }
}

