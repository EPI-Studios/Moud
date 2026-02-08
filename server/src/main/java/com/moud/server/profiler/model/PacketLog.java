package com.moud.server.profiler.model;

public record PacketLog(
        long timestamp,
        String direction,
        String packetName,
        int sizeBytes,
        Object packetObject
) {}