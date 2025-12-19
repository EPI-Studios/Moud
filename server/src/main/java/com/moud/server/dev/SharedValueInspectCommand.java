package com.moud.server.dev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.shared.SharedValueManager;
import com.moud.server.shared.diagnostics.SharedStoreSnapshot;
import com.moud.server.shared.diagnostics.SharedValueSnapshot;
import com.moud.server.permissions.PermissionManager;
import com.moud.server.permissions.ServerPermission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SharedValueInspectCommand extends Command {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_VALUE_LENGTH = 240;

    public SharedValueInspectCommand() {
        super("sharedinspect", "sharedvalues", "sharedstate");

        ArgumentString playerArg = ArgumentType.String("player");
        ArgumentString storeArg = ArgumentType.String("store");

        setDefaultExecutor((sender, context) -> {
            if (!ensureAllowed(sender)) {
                return;
            }
            if (sender instanceof Player player) {
                showStores(sender, player, null);
            } else {
                sender.sendMessage(Component.text("Usage: /sharedinspect <player> [store]"));
            }
        });

        addSyntax((sender, context) -> {
            if (!ensureAllowed(sender)) {
                return;
            }
            String playerToken = context.get(playerArg);
            String storeName = context.get(storeArg);
            Player target = resolvePlayer(sender, playerToken);
            if (target == null) {
                return;
            }
            showStores(sender, target, storeName);
        }, playerArg, storeArg);

        addSyntax((sender, context) -> {
            if (!ensureAllowed(sender)) {
                return;
            }
            String playerToken = context.get(playerArg);
            Player target = resolvePlayer(sender, playerToken);
            if (target == null) {
                return;
            }
            showStores(sender, target, null);
        }, playerArg);
    }

    private Player resolvePlayer(CommandSender sender, String token) {
        if (token == null || token.isBlank()) {
            if (sender instanceof Player player) {
                return player;
            }
            sender.sendMessage(Component.text("Provide a player name or UUID."));
            return null;
        }

        if ("self".equalsIgnoreCase(token) && sender instanceof Player player) {
            return player;
        }

        if ("all".equalsIgnoreCase(token) || "*".equals(token)) {
            sender.sendMessage(Component.text("Use /sharedinspect <player> to inspect an individual player."));
            return null;
        }

        Player byName = MinecraftServer.getConnectionManager()
                .getOnlinePlayers()
                .stream()
                .filter(p -> p.getUsername().equalsIgnoreCase(token))
                .findFirst()
                .orElse(null);

        if (byName != null) {
            return byName;
        }

        try {
            UUID uuid = UUID.fromString(token);
            return MinecraftServer.getConnectionManager().getOnlinePlayers()
                    .stream()
                    .filter(p -> p.getUuid().equals(uuid))
                    .findFirst()
                    .orElse(null);
        } catch (IllegalArgumentException ignored) {
            // not a UUID, fall through
        }

        sender.sendMessage(Component.text("Player '" + token + "' is not online."));
        return null;
    }

    private void showStores(CommandSender sender, Player target, String storeFilter) {
        SharedValueManager manager = SharedValueManager.getInstance();
        List<SharedStoreSnapshot> snapshots = new ArrayList<>();

        if (storeFilter != null && !storeFilter.isBlank()) {
            SharedStoreSnapshot store = manager.snapshotStore(target.getUuid().toString(), storeFilter);
            if (store != null) {
                snapshots.add(store);
            }
        } else {
            snapshots.addAll(manager.snapshotStoresForPlayer(target.getUuid().toString()));
        }

        if (snapshots.isEmpty()) {
            sender.sendMessage(Component.text("No shared values tracked for " + target.getUsername() + "."));
            return;
        }

        sender.sendMessage(Component.text(String.format(Locale.US,
                "Shared value snapshot for %s (%s)", target.getUsername(), target.getUuid())));

        long now = System.currentTimeMillis();

        for (SharedStoreSnapshot store : snapshots) {
            sender.sendMessage(Component.text(formatStoreHeader(store)));
            store.values().forEach((key, value) -> {
                String line = formatValueLine(value, now);
                sender.sendMessage(Component.text("  - " + line));
            });
        }
    }

    private boolean ensureAllowed(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (PermissionManager.getInstance().has(player, ServerPermission.DEV_UTILS)) {
            return true;
        }
        sender.sendMessage(Component.text("You do not have permission to use dev utilities.", NamedTextColor.RED));
        return false;
    }

    private String formatStoreHeader(SharedStoreSnapshot store) {
        return String.format(Locale.US,
                "Store '%s': keys=%d, listeners(change/key)=%d/%d",
                store.storeName(),
                store.totalKeys(),
                store.changeListenerCount(),
                store.keyListenerCount());
    }

    private String formatValueLine(SharedValueSnapshot snapshot, long now) {
        String rendered;
        try {
            rendered = MAPPER.writeValueAsString(snapshot.value());
        } catch (JsonProcessingException e) {
            rendered = String.valueOf(snapshot.value());
        }
        if (rendered.length() > MAX_VALUE_LENGTH) {
            rendered = rendered.substring(0, MAX_VALUE_LENGTH) + "…";
        }

        long ageMs = Math.max(0, now - snapshot.lastModified());
        String age = humanReadableAge(ageMs);

        String lastWriter = snapshot.lastWriter() + ":" + snapshot.lastWriterId();

        return String.format(Locale.US,
                "%s = %s [perm=%s, sync=%s, dirty=%s, last=%s, age=%s]",
                snapshot.key(),
                rendered,
                snapshot.permission(),
                snapshot.syncMode(),
                snapshot.dirty(),
                lastWriter,
                age);
    }

    private String humanReadableAge(long millis) {
        Duration duration = Duration.ofMillis(millis);
        if (duration.toHours() > 0) {
            return duration.toHours() + "h";
        }
        if (duration.toMinutes() > 0) {
            return duration.toMinutes() + "m";
        }
        if (duration.toSeconds() > 0) {
            return duration.toSeconds() + "s";
        }
        return millis + "ms";
    }
}
