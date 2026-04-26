package com.moud.client.fabric.render.mesh.draw;

import com.moud.client.fabric.render.mesh.cache.ClientMeshBindings;
import com.moud.core.mesh.ArrayMesh;
import com.moud.core.mesh.MeshRegistry;
import com.moud.core.mesh.Surface;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

public final class ProceduralMeshDrawer {
    private ProceduralMeshDrawer() {
    }

    public static boolean drawIfBound(
            long nodeId,
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            int light,
            int overlay,
            int r, int g, int b, int a) {
        var hash = ClientMeshBindings.hashFor(nodeId).orElse(null);
        if (hash == null) return false;
        var mesh = MeshRegistry.instance().get(hash).orElse(null);
        if (mesh == null) return false;
        boolean drew = false;
        for (Surface surface : mesh.surfaces()) {
            drew |= emitSurface(consumer, entry, surface, light, overlay, r, g, b, a);
        }
        return drew;
    }

    private static boolean emitSurface(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            Surface surface,
            int light,
            int overlay,
            int r, int g, int b, int a) {
        int[] indices = surface.indices();
        float[] positions = surface.positions();
        float[] normals = surface.normals();
        float[] uvs = surface.uvs();
        int[] colors = surface.hasColors() ? surface.colors() : null;
        if (indices.length < 3) {
            return false;
        }
        var tmpNormal = new Vector3f();
        for (int t = 0; t < indices.length; t += 3) {
            int ia = indices[t];
            int ib = indices[t + 1];
            int ic = indices[t + 2];
            emitVertex(consumer, entry, positions, normals, uvs, colors, ia, light, overlay, r, g, b, a, tmpNormal);
            emitVertex(consumer, entry, positions, normals, uvs, colors, ib, light, overlay, r, g, b, a, tmpNormal);
            emitVertex(consumer, entry, positions, normals, uvs, colors, ic, light, overlay, r, g, b, a, tmpNormal);
            // mc entity layers draw QUADS, emit a fourth vertex collapsed onto the third
            emitVertex(consumer, entry, positions, normals, uvs, colors, ic, light, overlay, r, g, b, a, tmpNormal);
        }
        return true;
    }

    private static void emitVertex(
            VertexConsumer consumer,
            MatrixStack.Entry entry,
            float[] positions,
            float[] normals,
            float[] uvs,
            int[] colors,
            int i,
            int light,
            int overlay,
            int r, int g, int b, int a,
            Vector3f tmpNormal) {
        int pi = i * 3;
        int ui = i * 2;
        int vr = r, vg = g, vb = b, va = a;
        if (colors != null) {
            int packed = colors[i];
            int vaIn = (packed >>> 24) & 0xFF;
            int vrIn = (packed >>> 16) & 0xFF;
            int vgIn = (packed >>> 8) & 0xFF;
            int vbIn = packed & 0xFF;
            vr = (r * vrIn + 127) / 255;
            vg = (g * vgIn + 127) / 255;
            vb = (b * vbIn + 127) / 255;
            va = (a * vaIn + 127) / 255;
        }
        tmpNormal.set(normals[pi], normals[pi + 1], normals[pi + 2]);
        consumer.vertex(entry.getPositionMatrix(), positions[pi], positions[pi + 1], positions[pi + 2])
                .color(vr, vg, vb, va)
                .texture(uvs[ui], uvs[ui + 1])
                .overlay(overlay)
                .light(light)
                .normal(entry, tmpNormal.x, tmpNormal.y, tmpNormal.z);
    }
}
