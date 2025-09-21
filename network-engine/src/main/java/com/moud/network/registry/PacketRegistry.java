package com.moud.network.registry;

import com.moud.network.annotation.Field;
import com.moud.network.annotation.Packet;
import com.moud.network.metadata.PacketMetadata;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketRegistry.class);

    private final ConcurrentHashMap<String, PacketMetadata> packetById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, PacketMetadata> packetByClass = new ConcurrentHashMap<>();

    public void scan(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> packetClasses = reflections.getTypesAnnotatedWith(Packet.class);

        for (Class<?> clazz : packetClasses) {
            register(clazz);
        }

        LOGGER.info("Registered {} packets from package '{}'", packetClasses.size(), basePackage);
    }

    private void register(Class<?> clazz) {
        Packet packetAnnotation = clazz.getAnnotation(Packet.class);
        String packetId = packetAnnotation.value();

        if (packetById.containsKey(packetId)) {
            throw new IllegalStateException("Duplicate packet ID: " + packetId);
        }

        List<PacketMetadata.FieldMetadata> fields = scanFields(clazz);
        validateFieldOrder(fields, clazz);

        com.moud.network.annotation.Direction annotationDirection = packetAnnotation.direction();
        PacketMetadata.Direction metadataDirection;
        switch (annotationDirection) {
            case CLIENT_TO_SERVER:
                metadataDirection = PacketMetadata.Direction.CLIENT_TO_SERVER;
                break;
            case SERVER_TO_CLIENT:
                metadataDirection = PacketMetadata.Direction.SERVER_TO_CLIENT;
                break;
            default: // default
                metadataDirection = PacketMetadata.Direction.BIDIRECTIONAL;
                break;
        }

        PacketMetadata metadata = new PacketMetadata(
                packetId,
                clazz,
                metadataDirection,
                fields
        );

        packetById.put(packetId, metadata);
        packetByClass.put(clazz, metadata);
        LOGGER.debug("Registered packet: {} -> {}", packetId, clazz.getSimpleName());
    }

    private List<PacketMetadata.FieldMetadata> scanFields(Class<?> clazz) {
        List<PacketMetadata.FieldMetadata> fields = new ArrayList<>();
        Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Field.class))
                .forEach(field -> {
                    Field fieldAnnotation = field.getAnnotation(Field.class);
                    fields.add(new PacketMetadata.FieldMetadata(field, fieldAnnotation.order(), fieldAnnotation.optional(), fieldAnnotation.maxLength()));
                });
        fields.sort(Comparator.comparingInt(PacketMetadata.FieldMetadata::getOrder));
        return fields;
    }

    private void validateFieldOrder(List<PacketMetadata.FieldMetadata> fields, Class<?> clazz) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).getOrder() != i) {
                throw new IllegalStateException("Invalid field order in " + clazz.getSimpleName() + ": expected " + i + ", got " + fields.get(i).getOrder());
            }
        }
    }

    public PacketMetadata getById(String packetId) {
        return packetById.get(packetId);
    }

    public PacketMetadata getByClass(Class<?> clazz) {
        return packetByClass.get(clazz);
    }

    public String getPacketId(Class<?> clazz) {
        PacketMetadata metadata = getByClass(clazz);
        return metadata != null ? metadata.packetId() : null;
    }

    public Class<?> getPacketClass(String packetId) {
        PacketMetadata metadata = getById(packetId);
        return metadata != null ? metadata.packetClass() : null;
    }

    public Collection<PacketMetadata> getAllMetadata() {
        return packetById.values();
    }
}