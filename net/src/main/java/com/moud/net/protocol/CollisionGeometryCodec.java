package com.moud.net.protocol;

import com.moud.core.physics.CollisionGeometry;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class CollisionGeometryCodec {

    public static final int CHUNK_PAYLOAD_BYTES = 256 * 1024;

    private CollisionGeometryCodec() {
    }

    public static byte[] serialize(CollisionGeometrySnapshot snapshot) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(baos)) {
            out.writeLong(snapshot.nodeId());
            List<CollisionGeometry> hulls = snapshot.hulls() == null ? List.of() : snapshot.hulls();
            out.writeInt(hulls.size());
            for (CollisionGeometry hull : hulls) {
                float[] vertices = hull.vertices();
                out.writeInt(vertices == null ? 0 : vertices.length);
                if (vertices != null) {
                    for (float f : vertices) out.writeFloat(f);
                }
                int[] indices = hull.indices();
                out.writeInt(indices == null ? 0 : indices.length);
                if (indices != null) {
                    for (int i : indices) out.writeInt(i);
                }
            }
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static CollisionGeometrySnapshot deserialize(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            long nodeId = in.readLong();
            int hullCount = in.readInt();
            List<CollisionGeometry> hulls = new ArrayList<>(hullCount);
            for (int i = 0; i < hullCount; i++) {
                int vLen = in.readInt();
                float[] vertices = new float[vLen];
                for (int j = 0; j < vLen; j++) vertices[j] = in.readFloat();
                int iLen = in.readInt();
                int[] indices = new int[iLen];
                for (int j = 0; j < iLen; j++) indices[j] = in.readInt();
                hulls.add(new CollisionGeometry(vertices, indices));
            }
            return new CollisionGeometrySnapshot(nodeId, hulls);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<CollisionGeometryChunk> split(CollisionGeometrySnapshot snapshot) {
        byte[] body = serialize(snapshot);
        int chunks = Math.max(1, (int) Math.ceil(body.length / (double) CHUNK_PAYLOAD_BYTES));
        List<CollisionGeometryChunk> out = new ArrayList<>(chunks);
        for (int i = 0; i < chunks; i++) {
            int start = i * CHUNK_PAYLOAD_BYTES;
            int end = Math.min(body.length, start + CHUNK_PAYLOAD_BYTES);
            byte[] slice = new byte[end - start];
            System.arraycopy(body, start, slice, 0, slice.length);
            out.add(new CollisionGeometryChunk(snapshot.nodeId(), i, chunks, slice));
        }
        return out;
    }
}
