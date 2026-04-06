package com.moud.client.fabric.render.preview;

import com.miry.graphics.Framebuffer;
import com.miry.graphics.Texture;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.render.material.MoudMaterial;
import com.moud.client.fabric.render.material.MoudMaterialParser;
import com.moud.client.fabric.render.material.MoudShaderFile;
import com.moud.client.fabric.render.mesh.MoudMeshBuffer;
import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import com.moud.client.fabric.render.veil.VeilMaterialBinding;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20C;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public final class MaterialPreviewRenderer {
    private static final Object LOCK = new Object();
    private static final int PREVIEW_SIZE = 128;
    private static final long PRUNE_AFTER_MS = 60_000L;

    private static final HashSet<String> requested = new HashSet<>();
    private static final HashMap<String, Entry> entries = new HashMap<>();

    private static String previewVert;
    private static ShaderProgram previewProgram;

    private MaterialPreviewRenderer() {}

    public static void request(String materialPath) {
        String p = norm(materialPath);
        if (!p.isEmpty()) {
            synchronized (LOCK) { requested.add(p); }
        }
    }

    public static Texture previewTexture(String materialPath) {
        String p = norm(materialPath);
        if (p.isEmpty()) return null;
        synchronized (LOCK) {
            Entry e = entries.get(p);
            return (e != null && e.framebuffer != null) ? e.framebuffer.colorTexture() : null;
        }
    }

    public static void renderRequested() {
        HashSet<String> toRender;
        synchronized (LOCK) {
            if (requested.isEmpty()) { prune(System.currentTimeMillis()); return; }
            toRender = new HashSet<>(requested);
            requested.clear();
        }

        long now = System.currentTimeMillis();
        for (String p : toRender) {
            Entry e;
            synchronized (LOCK) {
                e = entries.computeIfAbsent(p, Entry::new);
                e.lastUsedAtMs = now;
            }
            tryRender(e);
        }
        prune(now);
    }

    public static void clear() {
        synchronized (LOCK) {
            for (Entry e : entries.values()) {
                if (e != null && e.framebuffer != null) {
                    try { e.framebuffer.close(); } catch (Exception ignored) {}
                    e.framebuffer = null;
                }
            }
            entries.clear();
            requested.clear();
        }
    }

    public static void dropAll() {
        synchronized (LOCK) { entries.clear(); requested.clear(); }
    }

    private static void prune(long nowMs) {
        synchronized (LOCK) {
            entries.entrySet().removeIf(entry -> {
                Entry e = entry.getValue();
                if (e == null) return true;
                if (nowMs - e.lastUsedAtMs < PRUNE_AFTER_MS) return false;
                if (e.framebuffer != null) {
                    try { e.framebuffer.close(); } catch (Exception ignored) {}
                    e.framebuffer = null;
                }
                return true;
            });
        }
    }

    private static void tryRender(Entry e) {
        if (e == null) return;
        if (e.framebuffer == null) {
            e.framebuffer = new Framebuffer();
            e.dirty = true;
            clearFb(e.framebuffer);
        }

        String materialText = MoudTextAssets.readText(e.materialPath);
        if (materialText == null) return;

        if (!Objects.equals(materialText, e.cachedMaterialText)) {
            e.cachedMaterialText = materialText;
            e.cachedShaderText = null;
            e.dirty = true;
        }

        MoudMaterial material = MoudMaterialParser.parse(materialText);
        if (material == null) return;

        String shaderPath = material.shader() == null ? "" : material.shader().trim();
        if (shaderPath.isEmpty()) return;

        String shaderText = MoudTextAssets.readText(shaderPath);
        if (shaderText == null) return;

        if (!Objects.equals(shaderText, e.cachedShaderText)) {
            e.cachedShaderText = shaderText;
            e.dirty = true;
        }

        if (!e.binding.configure(e.materialPath, null)) return;

        ShaderProgram program = resolveMeshProgram(e.binding);
        if (program == null) return;

        Identifier pid = e.binding.programId();
        if (!Objects.equals(pid, e.cachedProgramId)) {
            e.cachedProgramId = pid;
            e.dirty = true;
        }

        if (!e.dirty) return;
        renderSphere(e, program);
        e.dirty = false;
    }

    private static ShaderProgram resolveMeshProgram(VeilMaterialBinding binding) {
        ShaderProgram direct = binding.resolveProgram();
        MoudShaderFile sf = binding.shaderFile();
        if (sf == null || binding.programId() == null) return null;

        Int2ObjectMap<String> stages = sf.stageSources();
        String vertSrc = stages.get(GL20C.GL_VERTEX_SHADER);
        boolean isBlit = vertSrc != null && vertSrc.contains("gl_VertexID");
        if (!isBlit) return direct;

        var meshId = Identifier.of(binding.programId().getNamespace(),
                "preview/" + binding.programId().getPath());
        Int2ObjectMap<String> meshStages = new Int2ObjectArrayMap<>(stages);
        meshStages.put(GL20C.GL_VERTEX_SHADER, getMeshVert());
        return VeilDynamicShaders.getOrCompile(meshId, meshStages);
    }

    private static void clearFb(Framebuffer fb) {
        if (fb == null) return;
        fb.ensureSize(PREVIEW_SIZE, PREVIEW_SIZE);
        try (Framebuffer.Binding ignored = fb.bindScoped()) {
            GL11.glClearColor(0.08f, 0.08f, 0.10f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        }
    }

    private static void renderSphere(Entry e, ShaderProgram program) {
        Framebuffer fb = e.framebuffer;
        if (fb == null) return;
        fb.ensureSize(PREVIEW_SIZE, PREVIEW_SIZE);

        MoudMeshBuffer.ensureSphereInitialized();

        int prevVp0 = 0, prevVp1 = 0, prevVp2 = 0, prevVp3 = 0;
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        prevVp0 = vp[0]; prevVp1 = vp[1]; prevVp2 = vp[2]; prevVp3 = vp[3];

        try (Framebuffer.Binding ignored = fb.bindScoped()) {
            GL11.glViewport(0, 0, PREVIEW_SIZE, PREVIEW_SIZE);
            GL11.glClearColor(0.08f, 0.08f, 0.10f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            try {
                VeilRenderSystem.setShader(program);
                program.bind();

                int pid = GlUtil.currentProgram();

                Matrix4f projMat = new Matrix4f().perspective((float) Math.toRadians(40), 1.0f, 0.1f, 50.0f);
                Matrix4f viewMat = new Matrix4f().lookAt(0, 0, 1.4f, 0, 0, 0, 0, 1, 0);
                Matrix4f worldMat = new Matrix4f().translate(-0.5f, -0.5f, -0.5f);
                Matrix4f modelMat = new Matrix4f(worldMat);

                GlUtil.uniformMat4(pid, "ModelMat", modelMat);
                GlUtil.uniformMat4(pid, "WorldMat", worldMat);
                GlUtil.uniformMat4(pid, "ViewMat", viewMat);
                GlUtil.uniformMat4(pid, "ProjMat", projMat);
                GlUtil.uniform4f(pid, "Tint", 1, 1, 1, 1);
                GlUtil.uniform3f(pid, "CameraPos", 0, 0, 1.4f);

                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.world != null) {
                    long totalTicks = client.world.getTime();
                    GlUtil.uniform1f(pid, "Time", totalTicks * 0.05f);
                    GlUtil.uniform1f(pid, "GameTime", totalTicks / 24_000.0f);
                    GlUtil.uniform1i(pid, "TimeTicks", (int) (client.world.getTimeOfDay() % 24_000L));
                }
                GlUtil.uniform1f(pid, "DeltaTime", 0);

                GlUtil.uniform1i(pid, "NumPointLights", 1);
                GlUtil.uniform3f(pid, "PointLights[0].position", 1.5f, 2.0f, 3.0f);
                GlUtil.uniform3f(pid, "PointLights[0].color", 1.0f, 0.95f, 0.9f);
                GlUtil.uniform1f(pid, "PointLights[0].brightness", 1.5f);
                GlUtil.uniform1f(pid, "PointLights[0].radius", 15.0f);
                GlUtil.uniform1i(pid, "NumDirLights", 0);
                GlUtil.uniform1i(pid, "NumSpotLights", 0);

                e.binding.applyMaterial(program);
                program.bindSamplers(0);

                long key = ((long) pid << 32) | (MoudMeshBuffer.sphereVbo() & 0xFFFFFFFFL);
                int vao = previewVaoCache.computeIfAbsent(key,
                        k -> GlUtil.createMeshVao(pid, MoudMeshBuffer.sphereVbo(), MoudMeshBuffer.sphereEbo()));
                GlUtil.drawElements(vao, MoudMeshBuffer.sphereIndexCount());
            } finally {
                ShaderProgram.unbind();
            }
        } finally {
            GL11.glViewport(prevVp0, prevVp1, prevVp2, prevVp3);
            RenderSystem.disableBlend();
        }
    }

    private static final HashMap<Long, Integer> previewVaoCache = new HashMap<>();

    private static String getMeshVert() {
        if (previewVert == null) {
            String fallback = loadResource("assets/moud/shaders/builtin/preview_mesh.vert", "");
            previewVert = loadResource("assets/moud/shaders/builtin/mesh_material.vert", fallback);
        }
        return previewVert;
    }

    private static String loadResource(String path, String fallback) {
        try (InputStream is = MaterialPreviewRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return fallback;
    }

    private static String norm(String v) {
        return v == null ? "" : v.trim();
    }

    private static final class Entry {
        final String materialPath;
        final VeilMaterialBinding binding = new VeilMaterialBinding();
        Framebuffer framebuffer;
        String cachedMaterialText;
        String cachedShaderText;
        Identifier cachedProgramId;
        boolean dirty = true;
        long lastUsedAtMs;

        Entry(String materialPath) { this.materialPath = materialPath; }
    }
}
