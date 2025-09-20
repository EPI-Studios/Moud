package com.moud.network.dispatcher;

import com.moud.network.metadata.PacketMetadata;
import com.moud.network.registry.PacketRegistry;
import com.moud.network.serializer.PacketSerializer;
import com.moud.network.buffer.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class NetworkDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkDispatcher.class);

    private final PacketRegistry registry;
    private final PacketSerializer serializer;
    private final ConcurrentHashMap<Class<?>, BiConsumer<Object, ?>> handlers = new ConcurrentHashMap<>();
    private final ByteBufferFactory bufferFactory;

    public NetworkDispatcher(PacketRegistry registry, PacketSerializer serializer, ByteBufferFactory bufferFactory) {
        this.registry = registry;
        this.serializer = serializer;
        this.bufferFactory = bufferFactory;
    }

    public <T> PacketData send(Object player, T packet) {
        PacketMetadata metadata = registry.getByClass(packet.getClass());
        if (metadata == null) {
            throw new IllegalArgumentException("Unregistered packet type: " + packet.getClass());
        }

        try {
            ByteBuffer buffer = bufferFactory.create();
            byte[] data = serializer.serialize(packet, metadata, buffer);
            return new PacketData(metadata.getPacketId(), data);
        } catch (Exception e) {
            LOGGER.error("Failed to send packet {}", metadata.getPacketId(), e);
            throw new RuntimeException("Packet send failed", e);
        }
    }

    public <T> void on(Class<T> packetClass, BiConsumer<Object, T> handler) {
        PacketMetadata metadata = registry.getByClass(packetClass);
        if (metadata == null) {
            throw new IllegalArgumentException("Unregistered packet type: " + packetClass);
        }

        handlers.put(packetClass, (BiConsumer<Object, ?>) handler);
        LOGGER.debug("Registered handler for packet: {}", metadata.getPacketId());
    }

    public void handle(String packetId, byte[] data, Object player) {
        PacketMetadata metadata = registry.getById(packetId);
        if (metadata == null) {
            LOGGER.warn("Received unknown packet: {}", packetId);
            return;
        }

        BiConsumer<Object, ?> handler = handlers.get(metadata.getPacketClass());
        if (handler == null) {
            LOGGER.warn("No handler for packet: {}", packetId);
            return;
        }

        try {
            ByteBuffer buffer = bufferFactory.wrap(data);
            Object packet = serializer.deserialize(data, metadata.getPacketClass(), metadata, buffer);
            ((BiConsumer<Object, Object>) handler).accept(player, packet);
        } catch (Exception e) {
            LOGGER.error("Failed to handle packet {}", packetId, e);
        }
    }

    public interface ByteBufferFactory {
        ByteBuffer create();
        ByteBuffer wrap(byte[] data);
    }

    public static class PacketData {
        private final String channel;
        private final byte[] data;

        public PacketData(String channel, byte[] data) {
            this.channel = channel;
            this.data = data;
        }

        public String getChannel() {
            return channel;
        }

        public byte[] getData() {
            return data;
        }
    }
}