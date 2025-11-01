package com.moud.server.network.diagnostics;

import net.minestom.server.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public final class NetworkProbe {
    private static final int TOP_PLAYERS = 5;
    private static final NetworkProbe INSTANCE = new NetworkProbe();

    private final ConcurrentMap<String, PacketStats> outboundStats = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PacketStats> inboundStats = new ConcurrentHashMap<>();

    private NetworkProbe() {
    }

    public static NetworkProbe getInstance() {
        return INSTANCE;
    }

    public void recordOutbound(Player player, String packetType, String channel, int payloadBytes, int totalBytes, long durationNanos, boolean success) {
        if (packetType == null) {
            return;
        }
        String playerKey = player != null ? player.getUuid().toString() : "server";
        outboundStats.computeIfAbsent(packetType, key -> new PacketStats())
                .record(playerKey, channel, payloadBytes, totalBytes, durationNanos, success);
    }

    public void recordInbound(Player player, String channel, int payloadBytes, long durationNanos, boolean success) {
        if (channel == null) {
            return;
        }
        String playerKey = player != null ? player.getUuid().toString() : "unknown";
        inboundStats.computeIfAbsent(channel, key -> new PacketStats())
                .record(playerKey, channel, payloadBytes, payloadBytes, durationNanos, success);
    }

    public NetworkSnapshot snapshot() {
        List<PacketStatSnapshot> outbound = outboundStats.entrySet()
                .stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparingLong(PacketStatSnapshot::totalBytes).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        List<PacketStatSnapshot> inbound = inboundStats.entrySet()
                .stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparingLong(PacketStatSnapshot::totalBytes).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        long outboundBytes = outbound.stream().mapToLong(PacketStatSnapshot::totalBytes).sum();
        long inboundBytes = inbound.stream().mapToLong(PacketStatSnapshot::totalBytes).sum();
        long outboundCount = outbound.stream().mapToLong(PacketStatSnapshot::totalCount).sum();
        long inboundCount = inbound.stream().mapToLong(PacketStatSnapshot::totalCount).sum();

        return new NetworkSnapshot(
                Instant.now(),
                outbound,
                inbound,
                outboundBytes,
                inboundBytes,
                outboundCount,
                inboundCount
        );
    }

    public void reset() {
        outboundStats.clear();
        inboundStats.clear();
    }

    private static final class PacketStats {
        private final LongAdder totalCount = new LongAdder();
        private final LongAdder totalBytes = new LongAdder();
        private final LongAdder payloadBytes = new LongAdder();
        private final LongAdder failureCount = new LongAdder();
        private final LongAdder totalLatencyNanos = new LongAdder();
        private final AtomicLong lastUpdated = new AtomicLong();
        private final ConcurrentMap<String, LongAdder> perPlayerCounts = new ConcurrentHashMap<>();
        private volatile String lastChannel = "";

        void record(String playerId, String channel, int payload, int total, long nanos, boolean success) {
            totalCount.increment();
            totalBytes.add(total);
            payloadBytes.add(payload);
            if (!success) {
                failureCount.increment();
            }
            if (nanos > 0) {
                totalLatencyNanos.add(nanos);
            }
            lastUpdated.set(System.currentTimeMillis());
            if (playerId != null) {
                perPlayerCounts.computeIfAbsent(playerId, id -> new LongAdder()).increment();
            }
            if (channel != null) {
                lastChannel = channel;
            }
        }

        PacketStatSnapshot snapshot(String identifier) {
            long count = totalCount.sum();
            double averageLatencyMs = count == 0 ? 0.0 : (double) totalLatencyNanos.sum() / (double) count / 1_000_000.0;

            Map<String, Long> topPlayers = perPlayerCounts.entrySet().stream()
                    .sorted((entryA, entryB) -> Long.compare(entryB.getValue().sum(), entryA.getValue().sum()))
                    .limit(TOP_PLAYERS)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().sum(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            return new PacketStatSnapshot(
                    identifier,
                    lastChannel,
                    count,
                    totalBytes.sum(),
                    payloadBytes.sum(),
                    failureCount.sum(),
                    averageLatencyMs,
                    lastUpdated.get(),
                    topPlayers
            );
        }
    }

    public record PacketStatSnapshot(
            String identifier,
            String channel,
            long totalCount,
            long totalBytes,
            long totalPayloadBytes,
            long failureCount,
            double averageLatencyMillis,
            long lastUpdatedEpochMillis,
            Map<String, Long> topPlayers
    ) {
        public boolean hasTraffic() {
            return totalCount > 0;
        }
    }

    public record NetworkSnapshot(
            Instant generatedAt,
            List<PacketStatSnapshot> outbound,
            List<PacketStatSnapshot> inbound,
            long outboundBytes,
            long inboundBytes,
            long outboundCount,
            long inboundCount
    ) {
    }
}
