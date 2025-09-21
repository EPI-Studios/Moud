package com.moud.server.network;

import com.moud.network.dispatcher.NetworkDispatcher;
import com.moud.network.engine.PacketEngine;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;

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

    public static <T> PluginMessagePacket createPacket(T packet) {
        NetworkDispatcher.PacketData packetData = DISPATCHER.send(null, packet);

        MinestomByteBuffer wrapperBuffer = new MinestomByteBuffer();
        wrapperBuffer.writeString(packetData.channel());
        wrapperBuffer.writeByteArray(packetData.data());

        return new PluginMessagePacket(WRAPPER_CHANNEL, wrapperBuffer.toByteArray());
    }

    public static <T> void registerHandler(Class<T> packetClass, java.util.function.BiConsumer<Object, T> handler) {
        DISPATCHER.on(packetClass, handler);
    }

    public static void handleIncoming(String channel, byte[] data, Object player) {
        DISPATCHER.handle(channel, data, player);
    }



}