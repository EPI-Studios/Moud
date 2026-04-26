package com.moud.core.mesh.io;

import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshPrimitive;
import com.moud.core.mesh.Surface;
import com.moud.core.mesh.build.MeshBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Binary serialization for {@link ArrayMesh}.
 *
 * <pre>
 * magic   : "MOUDMESH\0" (9 bytes)
 * version : u32 (currently 2; v1 had no color flag)
 * surfaces: u32
 * per surface:
 *   primitiveOrdinal : u32
 *   vertexCount      : u32
 *   indexCount       : u32
 *   flags            : u8  (bit 0: has colors)
 *   materialIdLen    : u32
 *   materialId       : utf8 bytes
 *   positions        : float[vertexCount * 3]
 *   normals          : float[vertexCount * 3]
 *   uvs              : float[vertexCount * 2]
 *   colors           : int[vertexCount]   (only if flags & 1)
 *   indices          : int[indexCount]
 * </pre>
 */
public final class MeshBinaryFormat {
    private static final byte[] MAGIC = new byte[]{'M', 'O', 'U', 'D', 'M', 'E', 'S', 'H', 0};
    private static final int VERSION = 2;
    private static final int FLAG_COLORS = 0x1;

    private MeshBinaryFormat() {
    }

    public static byte[] write(ArrayMesh mesh) {
        var baos = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(baos)) {
            out.write(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(mesh.surfaces().size());
            for (var surface : mesh.surfaces()) {
                boolean hasColors = surface.hasColors();
                out.writeInt(surface.primitive().ordinal());
                out.writeInt(surface.vertexCount());
                out.writeInt(surface.indices().length);
                out.writeByte(hasColors ? FLAG_COLORS : 0);
                var mat = surface.materialId() == null ? "" : surface.materialId();
                byte[] matBytes = mat.getBytes(StandardCharsets.UTF_8);
                out.writeInt(matBytes.length);
                out.write(matBytes);
                writeFloats(out, surface.positions());
                writeFloats(out, surface.normals());
                writeFloats(out, surface.uvs());
                if (hasColors) writeInts(out, surface.colors());
                writeInts(out, surface.indices());
            }
        } catch (IOException e) {
            throw new IllegalStateException("mesh write failed", e);
        }
        return baos.toByteArray();
    }

    public static ArrayMesh read(byte[] data) {
        try (var in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            if (magic.length != MAGIC.length) {
                throw new IllegalArgumentException("mesh file truncated");
            }
            for (int i = 0; i < MAGIC.length; i++) {
                if (magic[i] != MAGIC[i]) {
                    throw new IllegalArgumentException("mesh magic mismatch");
                }
            }
            int version = in.readInt();
            if (version != VERSION && version != 1) {
                throw new IllegalArgumentException("unsupported mesh version: " + version);
            }
            int surfaceCount = in.readInt();
            var builder = new MeshBuilder();
            for (int s = 0; s < surfaceCount; s++) {
                int primOrd = in.readInt();
                int vertexCount = in.readInt();
                int indexCount = in.readInt();
                int flags = version >= 2 ? (in.readByte() & 0xFF) : 0;
                int matLen = in.readInt();
                byte[] matBytes = in.readNBytes(matLen);
                String materialId = new String(matBytes, StandardCharsets.UTF_8);
                float[] positions = readFloats(in, vertexCount * 3);
                float[] normals = readFloats(in, vertexCount * 3);
                float[] uvs = readFloats(in, vertexCount * 2);
                int[] colors = (flags & FLAG_COLORS) != 0 ? readInts(in, vertexCount) : null;
                int[] indices = readInts(in, indexCount);
                MeshPrimitive prim = MeshPrimitive.values()[primOrd];
                builder.addSurface(new Surface(positions, normals, uvs, colors, indices, materialId, prim));
            }
            return builder.build();
        } catch (IOException e) {
            throw new IllegalStateException("mesh read failed", e);
        }
    }

    private static void writeFloats(DataOutputStream out, float[] data) throws IOException {
        for (float f : data) out.writeFloat(f);
    }

    private static void writeInts(DataOutputStream out, int[] data) throws IOException {
        for (int i : data) out.writeInt(i);
    }

    private static float[] readFloats(DataInputStream in, int count) throws IOException {
        float[] out = new float[count];
        for (int i = 0; i < count; i++) out[i] = in.readFloat();
        return out;
    }

    private static int[] readInts(DataInputStream in, int count) throws IOException {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = in.readInt();
        return out;
    }
}
