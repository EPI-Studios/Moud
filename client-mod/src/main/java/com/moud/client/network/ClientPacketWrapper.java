package com.moud.client.network;

import com.moud.network.dispatcher.NetworkDispatcher;
import com.moud.network.engine.PacketEngine;
import com.moud.client.network.buffer.FabricByteBuffer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

public class ClientPacketWrapper {
    private static final PacketEngine ENGINE = new PacketEngine();
    private static final NetworkDispatcher DISPATCHER;

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

        NetworkDispatcher.PacketData packetData = DISPATCHER.send(null, packet);

        DataPayload payload = new DataPayload(
                Identifier.of(packetData.channel()),
                packetData.data()
        );

        ClientPlayNetworking.send(payload);
    }

    public static <T> void registerHandler(Class<T> packetClass, java.util.function.BiConsumer<Object, T> handler) {
        DISPATCHER.on(packetClass, handler);
    }

    public static void handleIncoming(String channel, byte[] data, Object player) {
        DISPATCHER.handle(channel, data, player);
    }
}