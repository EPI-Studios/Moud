package com.moud.client.network;

import com.moud.client.MoudClientMod;
import com.moud.network.MoudPackets;
import com.moud.network.dispatcher.NetworkDispatcher;
import com.moud.network.engine.PacketEngine;
import com.moud.client.network.buffer.FabricByteBuffer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientPacketWrapper {
    private static final PacketEngine ENGINE = new PacketEngine();
    private static final NetworkDispatcher DISPATCHER;
    private static final boolean DEBUG_PACKET_RATE = Boolean.getBoolean("moud.debug.packetRate");
    private static long debugLastWorldTick = Long.MIN_VALUE;
    private static int debugSentThisTick = 0;
    private static final Map<String, Integer> debugSentByType = new HashMap<>();

    static {
        ENGINE.initialize("com.moud.network");
        DISPATCHER = ENGINE.createDispatcher(new NetworkDispatcher.ByteBufferFactory() {
            @Override
            public com.moud.network.buffer.ByteBuffer create() {
                return new FabricByteBuffer();
            }

            @Override
            public com.moud.network.buffer.ByteBuffer wrap(byte[] data) {
                return new FabricByteBuffer(data);
            }
        });
    }

    public static <T> void sendToServer(T packet) {
        if (!(packet instanceof MoudPackets.HelloPacket) && !MoudClientMod.isOnMoudServer()) {
            return;
        }
        debugTrack(packet);

        NetworkDispatcher.PacketData packetData = DISPATCHER.send(null, packet);

        DataPayload payload = new DataPayload(
                Identifier.of(packetData.channel()),
                packetData.data()
        );

        ClientPlayNetworking.send(payload);
    }

    private static void debugTrack(Object packet) {
        if (!DEBUG_PACKET_RATE || packet == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        long tick = client.world != null ? client.world.getTime() : Long.MIN_VALUE;
        if (tick != debugLastWorldTick) {
            maybeLogDebugRate();
            debugLastWorldTick = tick;
            debugSentThisTick = 0;
            debugSentByType.clear();
        }
        debugSentThisTick++;
        String type = packet.getClass().getSimpleName();
        debugSentByType.merge(type, 1, Integer::sum);
    }

    private static void maybeLogDebugRate() {
        if (debugSentThisTick <= 200 || debugSentByType.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(debugSentByType.entrySet());
        entries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        int limit = Math.min(8, entries.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            if (i > 0) sb.append(", ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        MoudClientMod.LOGGER.warn("High C2S packet rate in one tick: total={}, top={}", debugSentThisTick, sb);
    }

    public static <T> void registerHandler(Class<T> packetClass, java.util.function.BiConsumer<Object, T> handler) {
        DISPATCHER.on(packetClass, handler);
    }

    public static void handleIncoming(String channel, byte[] data, Object player) {
        DISPATCHER.handle(channel, data, player);
    }
}
