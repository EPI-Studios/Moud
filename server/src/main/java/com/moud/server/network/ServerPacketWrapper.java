package com.moud.server.network;

import com.moud.network.dispatcher.NetworkDispatcher;
import com.moud.network.engine.PacketEngine;
import com.moud.server.network.diagnostics.NetworkProbe;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;

import java.util.Objects;

public class ServerPacketWrapper {
    private static final PacketEngine ENGINE = new PacketEngine();
    private static final NetworkDispatcher DISPATCHER;
    private static final String WRAPPER_CHANNEL = "moud:wrapper";

    static {
        ENGINE.initialize("com.moud.network");
        DISPATCHER = ENGINE.createDispatcher(new NetworkDispatcher.ByteBufferFactory() {
            @Override
            public com.moud.network.buffer.ByteBuffer create() {
                return new MinestomByteBuffer();
            }

            @Override
            public com.moud.network.buffer.ByteBuffer wrap(byte[] data) {
                return new MinestomByteBuffer(data);
            }
        });
    }

    public static <T> PacketEnvelope wrapPacket(T packet) {
        Objects.requireNonNull(packet, "packet");
        NetworkDispatcher.PacketData packetData = DISPATCHER.send(null, packet);

        MinestomByteBuffer wrapperBuffer = new MinestomByteBuffer();
        wrapperBuffer.writeString(packetData.channel());
        wrapperBuffer.writeByteArray(packetData.data());
        byte[] payload = wrapperBuffer.toByteArray();

        return new PacketEnvelope(
                new PluginMessagePacket(WRAPPER_CHANNEL, payload),
                packetData.channel(),
                packet.getClass().getSimpleName(),
                packetData.data().length,
                payload.length
        );
    }

    public static <T> PluginMessagePacket createPacket(T packet) {
        return wrapPacket(packet).packet();
    }

    public static <T> void registerHandler(Class<T> packetClass, java.util.function.BiConsumer<Object, T> handler) {
        DISPATCHER.on(packetClass, handler);
    }

    public static void handleIncoming(String channel, byte[] data, Object player) {
        long start = System.nanoTime();
        boolean success = false;
        try {
            DISPATCHER.handle(channel, data, player);
            success = true;
        } finally {
            Player minestomPlayer = player instanceof Player ? (Player) player : null;
            NetworkProbe.getInstance().recordInbound(minestomPlayer, channel, data != null ? data.length : 0, System.nanoTime() - start, success);
        }
    }

    public record PacketEnvelope(PluginMessagePacket packet,
                                 String innerChannel,
                                 String packetType,
                                 int payloadBytes,
                                 int totalBytes) {
    }

}
