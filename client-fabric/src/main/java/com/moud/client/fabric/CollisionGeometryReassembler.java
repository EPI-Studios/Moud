package com.moud.client.fabric;

import com.moud.net.protocol.CollisionGeometryChunk;
import com.moud.net.protocol.CollisionGeometryCodec;
import com.moud.net.protocol.CollisionGeometrySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CollisionGeometryReassembler {

    private static final Map<Long, Buffer> BY_NODE = new HashMap<>();

    private CollisionGeometryReassembler() {
    }

    public static synchronized Optional<CollisionGeometrySnapshot> onChunk(CollisionGeometryChunk chunk) {
        if (chunk == null) return Optional.empty();
        long nodeId = chunk.nodeId();
        int total = chunk.chunkCount();
        if (total <= 0) return Optional.empty();
        Buffer buf = BY_NODE.computeIfAbsent(nodeId, k -> new Buffer(total));
        if (buf.expected != total) {
            buf = new Buffer(total);
            BY_NODE.put(nodeId, buf);
        }
        if (chunk.chunkIndex() < 0 || chunk.chunkIndex() >= total) return Optional.empty();
        if (buf.slices[chunk.chunkIndex()] == null) {
            buf.slices[chunk.chunkIndex()] = chunk.payload();
            buf.received++;
        }
        if (buf.received < total) return Optional.empty();
        BY_NODE.remove(nodeId);

        int totalLen = 0;
        for (byte[] s : buf.slices) totalLen += s.length;
        byte[] full = new byte[totalLen];
        int cursor = 0;
        for (byte[] s : buf.slices) {
            System.arraycopy(s, 0, full, cursor, s.length);
            cursor += s.length;
        }
        try {
            return Optional.of(CollisionGeometryCodec.deserialize(full));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static final class Buffer {
        final int expected;
        final byte[][] slices;
        int received;

        Buffer(int expected) {
            this.expected = expected;
            this.slices = new byte[expected][];
        }
    }
}
