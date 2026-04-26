package com.moud.core.mesh.io;

import com.moud.core.mesh.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class MeshHasher {
    private MeshHasher() {
    }

    public static String hash(List<Surface> surfaces) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var scratch = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            feedInt(digest, scratch, surfaces.size());
            for (var surface : surfaces) {
                feedInt(digest, scratch, surface.vertexCount());
                feedInt(digest, scratch, surface.indices().length);
                feedInt(digest, scratch, surface.primitive().ordinal());
                digest.update(surface.materialId() == null
                        ? new byte[0]
                        : surface.materialId().getBytes(StandardCharsets.UTF_8));
                digest.update(floatsToBytes(surface.positions()));
                digest.update(floatsToBytes(surface.normals()));
                digest.update(floatsToBytes(surface.uvs()));
                digest.update(intsToBytes(surface.indices()));
            }
            byte[] full = digest.digest();
            byte[] truncated = new byte[16];
            System.arraycopy(full, 0, truncated, 0, 16);
            return HexFormat.of().formatHex(truncated);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void feedInt(MessageDigest digest, ByteBuffer scratch, int value) {
        scratch.clear();
        scratch.putInt(value);
        digest.update(scratch.array(), 0, Integer.BYTES);
    }

    private static byte[] floatsToBytes(float[] data) {
        var buf = ByteBuffer.allocate(data.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buf.asFloatBuffer().put(data);
        return buf.array();
    }

    private static byte[] intsToBytes(int[] data) {
        var buf = ByteBuffer.allocate(data.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buf.asIntBuffer().put(data);
        return buf.array();
    }
}
