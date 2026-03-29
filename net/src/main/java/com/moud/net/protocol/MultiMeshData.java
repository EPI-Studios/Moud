package com.moud.net.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record MultiMeshData(long nodeId, int offset, int total, float[] data)
        implements Message, Chunkable<MultiMeshData> {

    @Override
    public MessageType type() {
        return MessageType.MULTIMESH_DATA;
    }

    @Override
    public List<MultiMeshData> chunk(int maxPayloadBytes) {
        float[] src = data == null ? new float[0] : data;
        int totalFloats = src.length;
        // 32 bytes: type varint + nodeId + offset varint + total varint + array-length varint + headroom
        int floatsPerChunk = Math.max(1, (maxPayloadBytes - 32) / Float.BYTES);
        if (totalFloats <= floatsPerChunk) {
            return List.of(this);
        }
        List<MultiMeshData> chunks = new ArrayList<>();
        for (int off = 0; off < totalFloats; off += floatsPerChunk) {
            int len = Math.min(floatsPerChunk, totalFloats - off);
            chunks.add(new MultiMeshData(nodeId, off, totalFloats, Arrays.copyOfRange(src, off, off + len)));
        }
        return chunks;
    }
}
