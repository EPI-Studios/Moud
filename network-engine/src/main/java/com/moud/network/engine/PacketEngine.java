package com.moud.network.engine;

import com.moud.network.dispatcher.NetworkDispatcher;
import com.moud.network.metadata.PacketMetadata;
import com.moud.network.registry.PacketRegistry;
import com.moud.network.serializer.PacketSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

public class PacketEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketEngine.class);

    private final PacketRegistry registry;
    private final PacketSerializer serializer;

    public PacketEngine() {
        this.registry = new PacketRegistry();
        this.serializer = new PacketSerializer();
    }

    public void initialize(String basePackage) {
        registry.scan(basePackage);
        LOGGER.info("Packet engine initialized and scanned package '{}'", basePackage);
    }


    public NetworkDispatcher createDispatcher(NetworkDispatcher.ByteBufferFactory bufferFactory) {
        return new NetworkDispatcher(registry, serializer, bufferFactory);
    }

    public String getPacketId(Class<?> packetClass) {
        PacketMetadata metadata = registry.getByClass(packetClass);
        return metadata != null ? metadata.getPacketId() : null;
    }

    public Class<?> getPacketClass(String packetId) {
        PacketMetadata metadata = registry.getById(packetId);
        return metadata != null ? metadata.getPacketClass() : null;
    }


    public Set<Class<?>> getAllPacketClasses() {
        return registry.getAllMetadata().stream()
                .map(PacketMetadata::getPacketClass)
                .collect(Collectors.toSet());
    }
}