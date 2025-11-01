package com.moud.server.dev;

import com.moud.server.network.diagnostics.NetworkProbe;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;

import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentInteger;
import net.minestom.server.entity.Player;

import java.util.Locale;
import java.util.UUID;

public final class NetworkProbeCommand extends Command {
    private static final int DEFAULT_LIMIT = 5;

    public NetworkProbeCommand() {
        super("networkprobe", "netprobe", "netstats");

        ArgumentInteger limitArg = ArgumentType.Integer("limit");
        limitArg.setDefaultValue(() -> DEFAULT_LIMIT);

        ArgumentLiteral resetLiteral = ArgumentType.Literal("reset");

        setDefaultExecutor((sender, context) -> sendSnapshot(sender, DEFAULT_LIMIT));

        addSyntax((sender, context) -> sendSnapshot(sender, context.get(limitArg)), limitArg);
        addSyntax((sender, context) -> {
            NetworkProbe.getInstance().reset();
            sender.sendMessage(Component.text("Network probe counters reset."));
        }, resetLiteral);
    }

    private void sendSnapshot(CommandSender sender, int limit) {
        NetworkProbe.NetworkSnapshot snapshot = NetworkProbe.getInstance().snapshot();

        sender.sendMessage(Component.text(String.format(Locale.US,
                "Outbound %d packets (%s), Inbound %d packets (%s)",
                snapshot.outboundCount(),
                humanBytes(snapshot.outboundBytes()),
                snapshot.inboundCount(),
                humanBytes(snapshot.inboundBytes()))));

        sender.sendMessage(Component.text("Top outbound packets:"));
        snapshot.outbound().stream()
                .filter(NetworkProbe.PacketStatSnapshot::hasTraffic)
                .limit(limit)
                .forEach(stat -> sender.sendMessage(Component.text("  - " + formatStat(stat))));

        sender.sendMessage(Component.text("Top inbound channels:"));
        snapshot.inbound().stream()
                .filter(NetworkProbe.PacketStatSnapshot::hasTraffic)
                .limit(limit)
                .forEach(stat -> sender.sendMessage(Component.text("  - " + formatStat(stat))));
    }

    private String formatStat(NetworkProbe.PacketStatSnapshot stat) {
        String base = String.format(Locale.US,
                "%s (%s): count=%d, payload=%s, avg=%.2fms, failures=%d",
                stat.identifier(),
                stat.channel(),
                stat.totalCount(),
                humanBytes(stat.totalPayloadBytes()),
                stat.averageLatencyMillis(),
                stat.failureCount());

        if (stat.topPlayers().isEmpty()) {
            return base;
        }

        StringBuilder players = new StringBuilder();
        stat.topPlayers().forEach((id, count) -> {
            if (players.length() > 0) {
                players.append(", ");
            }
            players.append(resolvePlayerName(id)).append("=").append(count);
        });

        return base + " [players: " + players + "]";
    }

    private String resolvePlayerName(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                    .filter(p -> p.getUuid().equals(uuid))
                    .map(Player::getUsername)
                    .findFirst()
                    .orElse(truncate(id));
        } catch (IllegalArgumentException e) {
            return truncate(id);
        }
    }

    private String truncate(String id) {
        int length = Math.min(id.length(), 8);
        return id.substring(0, length);
    }

    private String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.US, "%.2f %s", value, units[unitIndex]);
    }
}
