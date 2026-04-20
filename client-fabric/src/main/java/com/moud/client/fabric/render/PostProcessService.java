package com.moud.client.fabric.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.render.loading.PlayLoading;
import com.moud.client.fabric.render.material.MoudShaderFile;
import com.moud.client.fabric.render.material.MoudShaderParser;
import com.moud.client.fabric.render.veil.GlUtil;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import com.moud.core.assets.AssetHash;
import com.moud.core.assets.ResPath;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class PostProcessService {

    public static final PostProcessService INSTANCE = new PostProcessService();

    private static final String BUILTIN_SHADER_PREFIX = "assets/moud/shaders/builtin/";
    private static final String DEFAULT_VERTEX_SRC = loadBuiltin("default_blit.vert");
    private static final String FRAG_HEADER = loadBuiltin("post_process_header.frag");
    private static final Comparator<PostProcessEffect> EFFECT_ORDER =
            Comparator.comparingInt(PostProcessEffect::priority)
                    .thenComparingLong(PostProcessEffect::registrationOrder);

    private final ReentrantLock lock = new ReentrantLock();
    private final List<PostProcessEffect> effects = new ArrayList<>();
    private final Map<String, Integer> idIndex = new HashMap<>();

    private int pingFbo;
    private int pingTex;
    private int pongFbo;
    private int pongTex;
    private int pingPongDepthRbo;
    private int depthCopyFbo;
    private int depthCopyTex;
    private int ppWidth;
    private int ppHeight;

    private int fullscreenVao;

    private volatile float renderScale = 1.0f;

    private final long startTimeNanos = System.nanoTime();
    private long lastFrameNanos = startTimeNanos;
    private long nextRegistrationOrder = 1L;

    private PostProcessService() {
    }

    public void register(String id, String fragSrc) {
        registerInline(id, fragSrc, 0);
    }

    public void registerInline(String id, String fragSrc, int priority) {
        if (id == null || id.isBlank() || fragSrc == null) {
            return;
        }
        lock.lock();
        try {
            upsertEffect(id, PostProcessSourceKind.INLINE, fragSrc, priority);
        } finally {
            lock.unlock();
        }
    }

    public void registerShader(String id, String shaderPath) {
        registerShader(id, shaderPath, 0);
    }

    public void registerShader(String id, String shaderPath, int priority) {
        if (id == null || id.isBlank() || shaderPath == null || shaderPath.isBlank()) {
            return;
        }
        String normalized = shaderPath.trim();
        if (!normalized.startsWith(ResPath.SCHEME)) {
            return;
        }
        lock.lock();
        try {
            upsertEffect(id, PostProcessSourceKind.ASSET, normalized, priority);
        } finally {
            lock.unlock();
        }
    }

    public void unregister(String id) {
        if (id == null) {
            return;
        }
        lock.lock();
        try {
            Integer idx = idIndex.remove(id);
            if (idx == null) {
                return;
            }
            PostProcessEffect removed = effects.remove((int) idx);
            removed.program = null;
            rebuildIndex();
        } finally {
            lock.unlock();
        }
    }

    public void setUniform(String id, String key, float[] values) {
        if (id == null || key == null || values == null) {
            return;
        }
        lock.lock();
        try {
            Integer idx = idIndex.get(id);
            if (idx == null) {
                return;
            }
            effects.get(idx).floatUniforms.put(key, values.clone());
        } finally {
            lock.unlock();
        }
    }

    public void setTextureUniform(String id, String key, String textureRef) {
        if (id == null || key == null || textureRef == null || textureRef.isBlank()) {
            return;
        }
        lock.lock();
        try {
            Integer idx = idIndex.get(id);
            if (idx == null) {
                return;
            }
            effects.get(idx).textureUniforms.put(key, textureRef.trim());
        } finally {
            lock.unlock();
        }
    }

    public boolean hasEffect(String id) {
        lock.lock();
        try {
            return idIndex.containsKey(id);
        } finally {
            lock.unlock();
        }
    }

    public void setRenderScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) return;
        renderScale = Math.max(0.05f, Math.min(1.0f, scale));
    }

    public float getRenderScale() {
        return renderScale;
    }

    public int effectCount() {
        lock.lock();
        try {
            return effects.size();
        } finally {
            lock.unlock();
        }
    }

    public interface Overlay {
        void render(int width, int height);
    }

    public void renderAll(MinecraftClient client, int width, int height) {
        renderAll(client, width, height, null);
    }

    public void renderAll(MinecraftClient client, int width, int height, Overlay overlay) {
        if (client == null || width <= 0 || height <= 0) {
            return;
        }

        List<PostProcessEffectSnapshot> snapshot;
        lock.lock();
        try {
            if (effects.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(effects.size());
            for (PostProcessEffect effect : effects) {
                snapshot.add(new PostProcessEffectSnapshot(
                        effect.id,
                        effect.priority,
                        effect.registrationOrder,
                        effect.sourceKind,
                        effect.sourceValue,
                        new LinkedHashMap<>(effect.floatUniforms),
                        new LinkedHashMap<>(effect.textureUniforms)
                ));
            }
        } finally {
            lock.unlock();
        }

        Framebuffer mainFb = client.getFramebuffer();
        int sourceFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (sourceFbo == 0 && mainFb != null) {
            sourceFbo = mainFb.fbo;
        }
        if (sourceFbo == 0) {
            return;
        }

        float scale = renderScale;
        int rw = Math.max(1, Math.round(width * scale));
        int rh = Math.max(1, Math.round(height * scale));

        ensurePingPong(rw, rh);
        if (pingFbo == 0 || pongFbo == 0) {
            return;
        }

        if (sourceFbo != 0) {
            blitDepth(sourceFbo, width, height, rw, rh);
        }
        int depthTex = depthCopyTex;

        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);

        long nowNanos = System.nanoTime();
        float timeSec = (nowNanos - startTimeNanos) / 1_000_000_000f;
        float deltaSec = Math.min(1f, Math.max(0f, (nowNanos - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = nowNanos;

        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();

            // Downscale the full-res source into the low-res ping buffer.
            // Uses LINEAR here so high-frequency detail in the source doesn't
            // produce aliased dots; each subsequent effect pass runs entirely
            // at rw × rh.
            blitColorScaled(sourceFbo, pingFbo, width, height, rw, rh, GL11.GL_LINEAR);
            ensureFullscreenVao();

            int readTex = pingTex;
            int writeFbo = pongFbo;
            int drawnCount = 0;

            for (PostProcessEffectSnapshot effect : snapshot) {
                ShaderProgram program = ensureProgram(effect);
                if (program == null || !program.isValid()) {
                    continue;
                }

                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, writeFbo);
                GL11.glViewport(0, 0, rw, rh);
                GL11.glClearColor(0f, 0f, 0f, 0f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

                VeilRenderSystem.setShader(program);
                program.bind();
                int pid = GlUtil.currentProgram();

                int textureUnit = 0;
                bindTextureUnit(textureUnit, readTex);
                GlUtil.uniform1i(pid, "screen", textureUnit++);

                if (depthTex != 0) {
                    bindTextureUnit(textureUnit, depthTex);
                } else {
                    bindTextureUnit(textureUnit, 0);
                }
                GlUtil.uniform1i(pid, "depth", textureUnit++);

                GlUtil.uniform2f(pid, "Resolution", (float) rw, (float) rh);
                GlUtil.uniform2f(pid, "TexelSize", 1.0f / (float) rw, 1.0f / (float) rh);
                GlUtil.uniform1f(pid, "AspectRatio", (float) rw / (float) rh);
                GlUtil.uniform1f(pid, "Time", timeSec);
                GlUtil.uniform1f(pid, "DeltaTime", deltaSec);
                GlUtil.uniform1i(pid, "DepthAvailable", depthTex != 0 ? 1 : 0);

                SceneLights lights = VeilSceneRenderer.sharedLights();
                if (lights != null) {
                    lights.applyUniforms(pid);
                }

                org.joml.Matrix4f viewM = VeilSceneRenderer.lastViewMatrix();
                org.joml.Matrix4f projM = VeilSceneRenderer.lastProjectionMatrix();
                org.joml.Vector3f camP = VeilSceneRenderer.lastCameraPos();
                if (viewM != null && projM != null && camP != null) {
                    org.joml.Matrix4f vp = new org.joml.Matrix4f(projM).mul(viewM);
                    org.joml.Matrix4f invVp = new org.joml.Matrix4f(vp).invert();
                    GlUtil.uniformMat4(pid, "moud_viewProj", vp);
                    GlUtil.uniformMat4(pid, "moud_invViewProj", invVp);
                    GlUtil.uniform3f(pid, "moud_cameraPos", camP.x, camP.y, camP.z);
                }

                for (Map.Entry<String, float[]> u : effect.floatUniforms().entrySet()) {
                    float[] vals = u.getValue();
                    String key = u.getKey();
                    switch (vals.length) {
                        case 1 -> GlUtil.uniform1f(pid, key, vals[0]);
                        case 2 -> GlUtil.uniform2f(pid, key, vals[0], vals[1]);
                        case 3 -> GlUtil.uniform3f(pid, key, vals[0], vals[1], vals[2]);
                        case 4 -> GlUtil.uniform4f(pid, key, vals[0], vals[1], vals[2], vals[3]);
                        default -> {
                        }
                    }
                }

                for (Map.Entry<String, String> textureEntry : effect.textureUniforms().entrySet()) {
                    Identifier textureId = MoudTextures.resolve(textureEntry.getValue());
                    int glId = textureIdToGlId(client, textureId);
                    bindTextureUnit(textureUnit, glId);
                    GlUtil.uniform1i(pid, textureEntry.getKey(), textureUnit);
                    textureUnit++;
                }

                GL30.glBindVertexArray(fullscreenVao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                ShaderProgram.unbind();
                drawnCount++;

                if (readTex == pingTex) {
                    readTex = pongTex;
                    writeFbo = pingFbo;
                } else {
                    readTex = pingTex;
                    writeFbo = pongFbo;
                }
            }

            if (drawnCount > 0) {
                int finalFbo = readTex == pingTex ? pingFbo : pongFbo;

                // Upscale back to full framebuffer with NEAREST - this is
                // what gives the authentic chunky-pixel look when renderScale
                // is < 1.0. At scale=1.0 the src/dst are same size so there's
                // no resampling.
                blitColorScaled(finalFbo, sourceFbo, rw, rh, width, height, GL11.GL_NEAREST);
            }
        } finally {
            // Unbind raw-GL for units outside MC's tracked window (12+),
            // and sync the tracked units through GlStateManager so MC's
            // cache matches actual GL state - otherwise the next MC
            // draw may skip binding the correct texture thinking it's
            // already set, and calls into GlStateManager with
            // activeTexture >= 12 blow up BOUND_TEXTURES (length 12).
            for (int unit = 15; unit >= 12; unit--) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            for (int unit = 11; unit >= 0; unit--) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                GlStateManager._bindTexture(0);
            }
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);

            GL30.glBindVertexArray(prevVao);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);

            if (prevDepthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthMask(true);
            if (prevBlend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
        }
    }

    public void clear() {
        lock.lock();
        try {
            effects.clear();
            idIndex.clear();
        } finally {
            lock.unlock();
        }
        destroyPingPong();
        if (fullscreenVao != 0) {
            try {
                GL30.glDeleteVertexArrays(fullscreenVao);
            } catch (Exception ignored) {
            }
            fullscreenVao = 0;
        }
    }

    private void upsertEffect(String id, PostProcessSourceKind sourceKind, String sourceValue, int priority) {
        Integer idx = idIndex.get(id);
        PostProcessEffect effect;
        if (idx != null) {
            effect = effects.get(idx);
            effect.sourceKind = sourceKind;
            effect.sourceValue = sourceValue;
            effect.priority = priority;
            effect.programId = nextProgramId(id);
            effect.program = null;
            effect.assetVersion = Long.MIN_VALUE;
            effect.assetHash = null;
        } else {
            effect = new PostProcessEffect(id, sourceKind, sourceValue, priority, nextRegistrationOrder++);
            effect.programId = nextProgramId(id);
            effects.add(effect);
        }
        sortEffectsLocked();
    }

    private void sortEffectsLocked() {
        effects.sort(EFFECT_ORDER);
        rebuildIndex();
    }

    private void rebuildIndex() {
        idIndex.clear();
        for (int i = 0; i < effects.size(); i++) {
            idIndex.put(effects.get(i).id, i);
        }
    }

    private ShaderProgram ensureProgram(PostProcessEffectSnapshot effect) {
        lock.lock();
        try {
            Integer idx = idIndex.get(effect.id());
            if (idx == null) {
                return null;
            }
            PostProcessEffect live = effects.get(idx);
            if (live.sourceKind == PostProcessSourceKind.ASSET) {
                return ensureAssetProgram(live);
            }
            return ensureInlineProgram(live);
        } finally {
            lock.unlock();
        }
    }

    private ShaderProgram ensureInlineProgram(PostProcessEffect effect) {
        if (effect.program != null && effect.program.isValid()) {
            return effect.program;
        }
        String statusId = "shader:" + effect.programId;
        PlayLoading.pushStatus(statusId, "Compiling shaders");
        try {
            Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>();
            stages.put(GL20.GL_VERTEX_SHADER, DEFAULT_VERTEX_SRC);
            stages.put(GL20.GL_FRAGMENT_SHADER, FRAG_HEADER + "\n" + effect.sourceValue);
            ShaderProgram compiled = VeilDynamicShaders.getOrCompile(effect.programId, stages);
            if (compiled != null && compiled.isValid()) {
                effect.program = compiled;
            }
            return compiled;
        } finally {
            PlayLoading.popStatus(statusId);
        }
    }

    private ShaderProgram ensureAssetProgram(PostProcessEffect effect) {
        String shaderPath = effect.sourceValue;
        if (shaderPath == null || shaderPath.isBlank()) {
            return null;
        }
        String shaderText = MoudTextAssets.readText(shaderPath);
        if (shaderText == null || shaderText.isBlank()) {
            return null;
        }
        long version = MoudTextAssets.versionOf(shaderPath);
        if (effect.program != null && effect.program.isValid() && effect.assetVersion == version) {
            return effect.program;
        }

        MoudShaderFile shaderFile = MoudShaderParser.parse(shaderText, shaderPath);
        if (shaderFile == null) {
            return null;
        }
        Int2ObjectMap<String> stages = new Int2ObjectArrayMap<>(shaderFile.stageSources());
        String fragment = stages.get(GL20.GL_FRAGMENT_SHADER);
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        stages.put(GL20.GL_VERTEX_SHADER, defaultVertex(stages.get(GL20.GL_VERTEX_SHADER)));
        stages.put(GL20.GL_FRAGMENT_SHADER, FRAG_HEADER + "\n" + fragment);

        AssetHash hash = shaderFile.programHash();
        if (hash == null) {
            hash = AssetHash.sha256((shaderPath + "\n" + shaderText).getBytes(StandardCharsets.UTF_8));
        }
        effect.programId = Identifier.of("moud", "scripted/postprocess/" + hash.hex());
        String statusId = "shader:" + effect.programId;
        PlayLoading.pushStatus(statusId, "Compiling shaders");
        try {
            ShaderProgram compiled = VeilDynamicShaders.getOrCompile(effect.programId, stages);
            if (compiled != null && compiled.isValid()) {
                effect.program = compiled;
                effect.assetVersion = version;
                effect.assetHash = hash;
            }
            return compiled;
        } finally {
            PlayLoading.popStatus(statusId);
        }
    }

    private static String defaultVertex(String value) {
        return value == null || value.isBlank() ? DEFAULT_VERTEX_SRC : value;
    }

    private Identifier nextProgramId(String id) {
        String safe = id.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
        return Identifier.of("moud", "scripted/postprocess/" + safe + "/" + System.nanoTime());
    }

    private void ensurePingPong(int width, int height) {
        if (pingFbo != 0 && pongFbo != 0 && width == ppWidth && height == ppHeight) {
            return;
        }
        destroyPingPong();

        pingTex = createColorTexture(width, height);
        pongTex = createColorTexture(width, height);
        pingPongDepthRbo = createDepthRenderbuffer(width, height);
        pingFbo = createColorFbo(pingTex, pingPongDepthRbo);
        pongFbo = createColorFbo(pongTex, pingPongDepthRbo);
        ensureDepthCopy(width, height);
        ppWidth = width;
        ppHeight = height;
    }

    private static int createDepthRenderbuffer(int w, int h) {
        int rbo = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rbo);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, w, h);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        return rbo;
    }

    private static int createColorTexture(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        // NEAREST filter on ping-pong color textures so the low-res pipeline
        // stays pixel-perfect when renderScale < 1.0 (shaders sampling via
        // texture() get exact source pixels, no bilinear smear).
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int createColorFbo(int colorTex, int depthRbo) {
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTex, 0);
        if (depthRbo != 0) {
            GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRbo);
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        return fbo;
    }

    private void ensureDepthCopy(int w, int h) {
        if (depthCopyFbo != 0 && depthCopyTex != 0 && w == ppWidth && h == ppHeight) {
            return;
        }
        if (depthCopyFbo != 0) {
            try {
                GL30.glDeleteFramebuffers(depthCopyFbo);
            } catch (Exception ignored) {
            }
            depthCopyFbo = 0;
        }
        if (depthCopyTex != 0) {
            try {
                GL11.glDeleteTextures(depthCopyTex);
            } catch (Exception ignored) {
            }
            depthCopyTex = 0;
        }

        depthCopyTex = GL11.glGenTextures();
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthCopyTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT24, w, h, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);

        int prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevReadFb = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFb = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        depthCopyFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthCopyFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depthCopyTex, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFb);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFb);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFb);
    }

    private void blitDepth(int sourceFbo, int srcW, int srcH, int dstW, int dstH) {
        ensureDepthCopy(dstW, dstH);
        if (depthCopyFbo == 0) {
            return;
        }

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFbo);
        // Depth always uses NEAREST - averaging depths is nonsensical.
        GL30.glBlitFramebuffer(0, 0, srcW, srcH, 0, 0, dstW, dstH, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
    }

    private void destroyPingPong() {
        if (pingFbo != 0) {
            try {
                GL30.glDeleteFramebuffers(pingFbo);
            } catch (Exception ignored) {
            }
            pingFbo = 0;
        }
        if (pongFbo != 0) {
            try {
                GL30.glDeleteFramebuffers(pongFbo);
            } catch (Exception ignored) {
            }
            pongFbo = 0;
        }
        if (pingTex != 0) {
            try {
                GL11.glDeleteTextures(pingTex);
            } catch (Exception ignored) {
            }
            pingTex = 0;
        }
        if (pongTex != 0) {
            try {
                GL11.glDeleteTextures(pongTex);
            } catch (Exception ignored) {
            }
            pongTex = 0;
        }
        if (pingPongDepthRbo != 0) {
            try {
                GL30.glDeleteRenderbuffers(pingPongDepthRbo);
            } catch (Exception ignored) {
            }
            pingPongDepthRbo = 0;
        }
        if (depthCopyFbo != 0) {
            try {
                GL30.glDeleteFramebuffers(depthCopyFbo);
            } catch (Exception ignored) {
            }
            depthCopyFbo = 0;
        }
        if (depthCopyTex != 0) {
            try {
                GL11.glDeleteTextures(depthCopyTex);
            } catch (Exception ignored) {
            }
            depthCopyTex = 0;
        }
        ppWidth = 0;
        ppHeight = 0;
    }

    private void ensureFullscreenVao() {
        if (fullscreenVao == 0) {
            fullscreenVao = GL30.glGenVertexArrays();
        }
    }

    private static void blitColor(int sourceFbo, int destFbo, int w, int h) {
        blitColorScaled(sourceFbo, destFbo, w, h, w, h, GL11.GL_NEAREST);
    }

    private static void blitColorScaled(int sourceFbo, int destFbo,
                                        int srcW, int srcH, int dstW, int dstH, int filter) {
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destFbo);
        GL30.glBlitFramebuffer(0, 0, srcW, srcH, 0, 0, dstW, dstH, GL11.GL_COLOR_BUFFER_BIT, filter);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
    }

    private static void bindTextureUnit(int unit, int glId) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
    }

    private static int textureIdToGlId(MinecraftClient client, Identifier id) {
        if (client == null || id == null) {
            return 0;
        }
        TextureManager manager = client.getTextureManager();
        if (manager == null) {
            return 0;
        }
        AbstractTexture texture = manager.getTexture(id);
        return texture != null ? texture.getGlId() : 0;
    }

    private static String loadBuiltin(String name) {
        try (InputStream is = PostProcessService.class.getClassLoader()
                .getResourceAsStream(BUILTIN_SHADER_PREFIX + name)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

}
