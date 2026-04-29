package com.moud.client.fabric.render;

import com.moud.client.fabric.render.mesh.ProceduralMeshGpuCache;
import com.moud.client.fabric.render.mesh.cache.ClientMeshBindings;
import com.moud.client.fabric.render.veil.VeilMaterialBinding;
import com.moud.net.protocol.SceneSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

public final class ScenePreloader {
    private ScenePreloader() {}

    public static void preload(SceneSnapshot snapshot) {
        if (snapshot == null) return;
        List<SceneSnapshot.NodeSnapshot> nodes = snapshot.nodes();
        if (nodes == null || nodes.isEmpty()) return;

        Set<String> seenTextures = new HashSet<>();
        Set<String> seenMeshes = new HashSet<>();
        Set<String> seenMaterials = new HashSet<>();
        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null) continue;

            String tex = property(node, "texture");
            if (tex != null && !tex.isBlank() && seenTextures.add(tex)) {
                touchTexture(tex);
            }

            String mat = property(node, "material");
            if (mat != null && !mat.isBlank() && seenMaterials.add(mat)) {
                touchMaterial(mat);
            }

            ClientMeshBindings.hashFor(node.nodeId()).ifPresent(hash -> {
                if (seenMeshes.add(hash)) {
                    touchMesh(hash);
                }
            });
        }
    }

    private static void touchMaterial(String materialPath) {
        RenderSystem.recordRenderCall(() -> {
            try {
                VeilMaterialBinding probe = new VeilMaterialBinding();
                probe.configure(materialPath, null);
                probe.resolveProgram();
            } catch (Throwable ignored) {
            }
        });
    }

    private static void touchTexture(String ref) {
        Identifier id = MoudTextures.resolve(ref);
        if (id == null) return;
        RenderSystem.recordRenderCall(() -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getTextureManager() == null) return;
            AbstractTexture tex = mc.getTextureManager().getTexture(id);
            if (tex != null) {
                tex.getGlId();
            }
        });
    }

    private static void touchMesh(String hash) {
        RenderSystem.recordRenderCall(() -> {
            ProceduralMeshGpuCache.getOrUpload(hash);
        });
    }

    public static void preloadMesh(String hash) {
        if (hash == null || hash.isBlank()) return;
        touchMesh(hash);
    }

    private static String property(SceneSnapshot.NodeSnapshot node, String key) {
        if (node.properties() == null) return null;
        for (SceneSnapshot.Property p : node.properties()) {
            if (p != null && key.equals(p.key())) return p.value();
        }
        return null;
    }
}
