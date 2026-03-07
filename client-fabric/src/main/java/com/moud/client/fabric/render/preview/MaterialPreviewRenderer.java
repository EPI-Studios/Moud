package com.moud.client.fabric.render.preview;

import com.miry.graphics.Framebuffer;
import com.miry.graphics.Texture;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.render.material.MoudMaterial;
import com.moud.client.fabric.render.material.MoudMaterialParser;
import com.moud.client.fabric.render.veil.VeilMaterialBinding;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

public final class MaterialPreviewRenderer {
    private static final Object LOCK = new Object();

    private static final int PREVIEW_SIZE = 128;
    private static final long PRUNE_AFTER_MS = 60_000L;

    private static final HashSet<String> requested = new HashSet<>();
    private static final HashMap<String, Entry> entries = new HashMap<>();

    private MaterialPreviewRenderer() {
    }

    public static void request(String materialPath) {
        String p = norm(materialPath);
        if (p.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            requested.add(p);
        }
    }

    public static Texture previewTexture(String materialPath) {
        String p = norm(materialPath);
        if (p.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            Entry e = entries.get(p);
            if (e == null || e.framebuffer == null) {
                return null;
            }
            return e.framebuffer.colorTexture();
        }
    }

    public static void renderRequested() {
        HashSet<String> toRender;
        synchronized (LOCK) {
            if (requested.isEmpty()) {
                prune(System.currentTimeMillis());
                return;
            }
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
                    try {
                        e.framebuffer.close();
                    } catch (Exception ignored) {
                    }
                    e.framebuffer = null;
                }
            }
            entries.clear();
            requested.clear();
        }
    }

    public static void dropAll() {
        synchronized (LOCK) {
            entries.clear();
            requested.clear();
        }
    }

    private static void prune(long nowMs) {
        synchronized (LOCK) {
            entries.entrySet().removeIf(entry -> {
                Entry e = entry.getValue();
                if (e == null) {
                    return true;
                }
                if (nowMs - e.lastUsedAtMs < PRUNE_AFTER_MS) {
                    return false;
                }
                if (e.framebuffer != null) {
                    try {
                        e.framebuffer.close();
                    } catch (Exception ignored) {
                    }
                    e.framebuffer = null;
                }
                return true;
            });
        }
    }

    private static void tryRender(Entry e) {
        if (e == null) {
            return;
        }
        if (e.framebuffer == null) {
            e.framebuffer = new Framebuffer();
            e.dirty = true;
            clearToDefault(e.framebuffer);
        }

        String materialText = MoudTextAssets.readText(e.materialPath);
        if (materialText == null) {
            return;
        }

        if (!Objects.equals(materialText, e.cachedMaterialText)) {
            e.cachedMaterialText = materialText;
            e.cachedShaderText = null;
            e.dirty = true;
        }

        MoudMaterial material = MoudMaterialParser.parse(materialText);
        if (material == null) {
            return;
        }

        String shaderPath = material.shader() == null ? "" : material.shader().trim();
        if (shaderPath.isEmpty()) {
            return;
        }

        String shaderText = MoudTextAssets.readText(shaderPath);
        if (shaderText == null) {
            return;
        }

        if (!Objects.equals(shaderText, e.cachedShaderText)) {
            e.cachedShaderText = shaderText;
            e.dirty = true;
        }

        if (!e.dirty) {
            return;
        }

        if (!e.binding.configure(e.materialPath, null)) {
            return;
        }

        ShaderProgram program = e.binding.resolveProgram();
        if (program == null) {
            return;
        }

        renderInto(e, program);
        e.dirty = false;
    }

    private static void clearToDefault(Framebuffer fb) {
        if (fb == null) {
            return;
        }
        fb.ensureSize(PREVIEW_SIZE, PREVIEW_SIZE);
        try (Framebuffer.Binding ignored = fb.bindScoped()) {
            GL11.glClearColor(0.10f, 0.10f, 0.10f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    }

    private static void renderInto(Entry e, ShaderProgram program) {
        Framebuffer fb = e.framebuffer;
        if (fb == null) {
            return;
        }
        fb.ensureSize(PREVIEW_SIZE, PREVIEW_SIZE);

        boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMaskWasEnabled = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer b = stack.malloc(1);
            GL11.glGetBooleanv(GL11.GL_DEPTH_WRITEMASK, b);
            depthMaskWasEnabled = b.get(0) != 0;
        } catch (Exception ignored) {
        }

        try (Framebuffer.Binding ignored = fb.bindScoped()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            GL11.glClearColor(0.10f, 0.10f, 0.10f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            try {
                VeilRenderSystem.setShader(program);
                program.bind();
                program.setDefaultUniforms(VertexFormat.DrawMode.TRIANGLE_STRIP);
                applyPreviewUniforms(program);
                e.binding.applyMaterial(program);
                program.bindSamplers(0);
                VeilRenderSystem.drawScreenQuad();
            } finally {
                ShaderProgram.unbind();
            }
        } finally {
            RenderSystem.depthMask(depthMaskWasEnabled);
            if (depthTestWasEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (blendWasEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
        }
    }

    private static void applyPreviewUniforms(ShaderProgram program) {
        if (program == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        int timeTicks = (int) (client.world.getTimeOfDay() % 24_000L);
        if (program.hasUniform("moud_timeTicks")) {
            program.getUniformSafe("moud_timeTicks").setInt(timeTicks);
        }
        if (program.hasUniform("moud_time01")) {
            program.getUniformSafe("moud_time01").setFloat((float) (timeTicks / 24_000.0));
        }
    }

    private static String norm(String v) {
        if (v == null) {
            return "";
        }
        return v.trim();
    }

    private static final class Entry {
        private final String materialPath;
        private final VeilMaterialBinding binding = new VeilMaterialBinding();

        private Framebuffer framebuffer;
        private String cachedMaterialText;
        private String cachedShaderText;
        private boolean dirty = true;
        private long lastUsedAtMs;

        private Entry(String materialPath) {
            this.materialPath = materialPath;
        }
    }
}
