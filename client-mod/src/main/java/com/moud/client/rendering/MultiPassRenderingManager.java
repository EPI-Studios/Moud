package com.moud.client.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferAttachmentDefinition;
import foundry.veil.api.client.render.framebuffer.FramebufferManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_VIEWPORT;

public final class MultiPassRenderingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultiPassRenderingManager.class);
    private static final Identifier MAIN_FRAMEBUFFER = Identifier.of("minecraft", "main");

    private final Map<Identifier, FramebufferSpec> managedFramebuffers = new ConcurrentHashMap<>();
    private final Map<String, RenderPassSpec> passes = new ConcurrentHashMap<>();

    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;

    public MultiPassRenderingManager() {
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(
                (stage,
                 levelRenderer,
                 bufferSource,
                 matrixStack,
                 frustumMatrix,
                 projectionMatrix,
                 renderTick,
                 deltaTracker,
                 camera,
                 frustum) -> {
                    if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
                        return;
                    }
                    if (stage == VeilRenderLevelStageEvent.Stage.AFTER_SKY) {
                        beginFrame();
                    }
                    runStage(stage, projectionMatrix, deltaTracker, camera);
                }
        );
    }

    public void createFramebuffer(String framebufferId, @Nullable Map<String, Object> options) {
        Identifier id = Identifier.tryParse(framebufferId);
        if (id == null) {
            LOGGER.warn("[MultiPass] Invalid framebuffer id: {}", framebufferId);
            return;
        }
        FramebufferSpec spec = FramebufferSpec.fromOptions(id, options);
        managedFramebuffers.put(id, spec);

        MinecraftClient.getInstance().execute(() -> createOrResizeFramebuffer(spec, true));
    }

    public void removeFramebuffer(String framebufferId) {
        Identifier id = Identifier.tryParse(framebufferId);
        if (id == null) {
            LOGGER.warn("[MultiPass] Invalid framebuffer id: {}", framebufferId);
            return;
        }
        FramebufferSpec removed = managedFramebuffers.remove(id);
        if (removed == null) {
            return;
        }
        if (MAIN_FRAMEBUFFER.equals(id)) {
            LOGGER.warn("[MultiPass] Refusing to remove minecraft:main");
            return;
        }
        MinecraftClient.getInstance().execute(() -> {
            FramebufferManager framebufferManager = VeilRenderSystem.renderer().getFramebufferManager();
            AdvancedFbo old = framebufferManager.removeFramebuffer(id);
            if (old != null) {
                old.free();
            }
        });
    }

    public void defineRenderPass(String passId, @Nullable Map<String, Object> options) {
        RenderPassSpec spec = RenderPassSpec.fromOptions(passId, options);
        if (spec == null) {
            return;
        }
        passes.put(passId, spec);
    }

    public void removeRenderPass(String passId) {
        passes.remove(passId);
    }

    public void setRenderPassEnabled(String passId, boolean enabled) {
        RenderPassSpec existing = passes.get(passId);
        if (existing == null) {
            return;
        }
        passes.put(passId, existing.withEnabled(enabled));
    }

    public void reset() {
        passes.clear();
        Map<Identifier, FramebufferSpec> toRemove = new HashMap<>(managedFramebuffers);
        managedFramebuffers.clear();
        MinecraftClient.getInstance().execute(() -> {
            FramebufferManager framebufferManager = VeilRenderSystem.renderer().getFramebufferManager();
            for (Identifier id : toRemove.keySet()) {
                if (MAIN_FRAMEBUFFER.equals(id)) {
                    continue;
                }
                AdvancedFbo old = framebufferManager.removeFramebuffer(id);
                if (old != null) {
                    old.free();
                }
            }
        });
    }

    private void beginFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        int screenWidth = client.getWindow().getFramebufferWidth();
        int screenHeight = client.getWindow().getFramebufferHeight();

        boolean screenSizeChanged = screenWidth != lastScreenWidth || screenHeight != lastScreenHeight;
        if (screenSizeChanged) {
            lastScreenWidth = screenWidth;
            lastScreenHeight = screenHeight;
        }

        for (FramebufferSpec spec : managedFramebuffers.values()) {
            if (spec.autoClear() && !MAIN_FRAMEBUFFER.equals(spec.id())) {
                AdvancedFbo fbo = getFramebuffer(spec.id());
                if (fbo != null) {
                    ClearColor clearColor = spec.clearColor();
                    fbo.clear(
                            clearColor.r(),
                            clearColor.g(),
                            clearColor.b(),
                            clearColor.a(),
                            clearColor.depth(),
                            fbo.getClearMask()
                    );
                }
            }

            boolean missing = getFramebuffer(spec.id()) == null;
            if (spec.size().mode() == SizeMode.SCREEN || screenSizeChanged || missing) {
                createOrResizeFramebuffer(spec, false);
            }
        }
    }

    private void createOrResizeFramebuffer(FramebufferSpec spec, boolean force) {
        if (spec.id().equals(MAIN_FRAMEBUFFER)) {
            LOGGER.warn("[MultiPass] Refusing to override minecraft:main");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        int targetWidth = spec.size().resolveWidth(client.getWindow().getFramebufferWidth());
        int targetHeight = spec.size().resolveHeight(client.getWindow().getFramebufferHeight());

        AdvancedFbo current = getFramebuffer(spec.id());
        if (!force && current != null && current.getWidth() == targetWidth && current.getHeight() == targetHeight) {
            return;
        }

        AdvancedFbo.Builder builder = AdvancedFbo.withSize(targetWidth, targetHeight)
                .setDebugLabel(spec.id().toString());
        for (AttachmentSpec attachment : spec.colorAttachments()) {
            configureAttachment(builder, attachment);
            if (attachment.type == AttachmentType.RENDER_BUFFER) {
                builder.addColorRenderBuffer();
            } else {
                builder.addColorTextureBuffer();
            }
        }

        if (spec.depthAttachment() != null) {
            configureAttachment(builder, spec.depthAttachment());
            if (spec.depthAttachment().type == AttachmentType.RENDER_BUFFER) {
                builder.setDepthRenderBuffer();
            } else {
                builder.setDepthTextureBuffer();
            }
        }

        AdvancedFbo fbo;
        try {
            fbo = builder.build(true);
        } catch (Exception e) {
            LOGGER.error("[MultiPass] Failed to build framebuffer {}", spec.id(), e);
            return;
        }

        FramebufferManager framebufferManager = VeilRenderSystem.renderer().getFramebufferManager();
        AdvancedFbo old = framebufferManager.removeFramebuffer(spec.id());
        if (old != null) {
            old.free();
        }
        framebufferManager.setFramebuffer(spec.id(), fbo);
        ClearColor clearColor = spec.clearColor();
        fbo.clear(
                clearColor.r(),
                clearColor.g(),
                clearColor.b(),
                clearColor.a(),
                clearColor.depth(),
                fbo.getClearMask()
        );
        FramebufferTextureExports.update(spec.id(), fbo);
    }

    private void configureAttachment(AdvancedFbo.Builder builder, AttachmentSpec attachment) {
        builder.setLevels(Math.max(1, attachment.levels));
        builder.setFormat(attachment.format);
        builder.setFilter(attachment.linear, false);
        builder.setName(attachment.name);
    }

    private void runStage(
            VeilRenderLevelStageEvent.Stage stage,
            Matrix4fc projectionMatrix,
            RenderTickCounter deltaTracker,
            Camera camera
    ) {
        String stageName = stage.getName();
        List<Map.Entry<String, RenderPassSpec>> stagePasses = new ArrayList<>();
        for (Map.Entry<String, RenderPassSpec> entry : passes.entrySet()) {
            RenderPassSpec spec = entry.getValue();
            if (!spec.enabled) {
                continue;
            }
            if (!stageName.equals(spec.stage)) {
                continue;
            }
            stagePasses.add(entry);
        }

        if (stagePasses.isEmpty()) {
            return;
        }

        stagePasses.sort(Comparator.comparingInt((Map.Entry<String, RenderPassSpec> entry) -> entry.getValue().order)
                .thenComparing(Map.Entry::getKey));

        RenderContext context = new RenderContext(projectionMatrix, deltaTracker, camera);
        for (Map.Entry<String, RenderPassSpec> entry : stagePasses) {
            try {
                executePass(entry.getKey(), entry.getValue(), context);
            } catch (Exception e) {
                LOGGER.error("[MultiPass] Pass '{}' crashed", entry.getKey(), e);
            }
        }
    }

    private void executePass(String passId, RenderPassSpec spec, RenderContext context) {
        switch (spec.type) {
            case BLIT -> executeBlitPass(passId, spec.asBlit(), context);
            case COPY -> executeCopyPass(passId, spec.asCopy());
            case WORLD -> executeWorldPass(passId, spec.asWorld(), context);
            case CLEAR -> executeClearPass(passId, spec.asClear());
            default -> LOGGER.warn("[MultiPass] Unknown pass type for '{}': {}", passId, spec.type);
        }
    }

    private void executeClearPass(String passId, ClearPassSpec spec) {
        AdvancedFbo target = getFramebuffer(spec.target);
        if (target == null) {
            LOGGER.debug("[MultiPass] Clear pass '{}' skipped (missing target framebuffer {})", passId, spec.target);
            return;
        }
        FramebufferState state = FramebufferState.capture();
        try {
            target.bind(true);
            clearFramebuffer(spec.target, target, spec.clearColor);
            FramebufferTextureExports.update(spec.target, target);
        } finally {
            state.restore();
        }
    }

    private void executeCopyPass(String passId, CopyPassSpec spec) {
        AdvancedFbo in = getFramebuffer(spec.in);
        AdvancedFbo out = getFramebuffer(spec.out);

        if (in == null || out == null) {
            LOGGER.debug("[MultiPass] Copy pass '{}' skipped (in={}, out={})", passId, spec.in, spec.out);
            return;
        }

        int mask = 0;
        if (spec.color) {
            mask |= GL_COLOR_BUFFER_BIT;
        }
        if (spec.depth) {
            mask |= GL_DEPTH_BUFFER_BIT;
        }
        if (mask == 0) {
            mask = GL_COLOR_BUFFER_BIT;
        }

        int filtering = spec.linear ? GL_LINEAR : GL_NEAREST;
        FramebufferState state = FramebufferState.capture();
        try {
            in.resolveToAdvancedFbo(out, mask, filtering);
            FramebufferTextureExports.update(spec.out, out);
        } finally {
            state.restore();
        }
    }

    private void executeBlitPass(String passId, BlitPassSpec spec, RenderContext context) {
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(spec.shader);
        if (shader == null) {
            if (spec.printErrorOnce()) {
                LOGGER.warn("[MultiPass] Blit pass '{}' missing shader {}", passId, spec.shader);
            }
            return;
        }

        AdvancedFbo out = getFramebuffer(spec.out);
        if (out == null) {
            LOGGER.debug("[MultiPass] Blit pass '{}' skipped (missing out framebuffer {})", passId, spec.out);
            return;
        }

        AdvancedFbo in = spec.in != null ? getFramebuffer(spec.in) : null;

        FramebufferState state = FramebufferState.capture();
        try {
            shader.bind();
            if (in != null) {
                shader.setFramebufferSamplers(in);
            }

            out.bind(true);
            if (spec.clearOut) {
                clearFramebuffer(spec.out, out, null);
            }

            applyInOutSizeUniforms(shader, in, out);
            shader.setDefaultUniforms(net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_STRIP);
            shader.bindSamplers(0);
            applyUniformMap(shader, spec.uniforms);
            VeilRenderSystem.drawScreenQuad();
            shader.clearSamplers();
            FramebufferTextureExports.update(spec.out, out);
        } finally {
            ShaderProgram.unbind();
            state.restore();
        }
    }

    private void applyInOutSizeUniforms(ShaderProgram shader, @Nullable AdvancedFbo in, AdvancedFbo out) {
        ShaderUniform inSize = shader.getUniform("InSize");
        if (inSize != null) {
            if (in != null) {
                inSize.setVector(in.getWidth(), in.getHeight());
            } else {
                inSize.setVector(1.0F, 1.0F);
            }
        }
        ShaderUniform outSize = shader.getUniform("OutSize");
        if (outSize != null) {
            outSize.setVector(out.getWidth(), out.getHeight());
        }
    }

    private void executeWorldPass(String passId, WorldPassSpec spec, RenderContext context) {
        AdvancedFbo out = getFramebuffer(spec.out);
        if (out == null) {
            LOGGER.debug("[MultiPass] World pass '{}' skipped (missing out framebuffer {})", passId, spec.out);
            return;
        }

        Vector3d cameraPos;
        Quaternionf cameraRot;
        if (spec.cameraPosition != null) {
            cameraPos = new Vector3d(spec.cameraPosition.x, spec.cameraPosition.y, spec.cameraPosition.z);
        } else {
            var pos = context.camera.getPos();
            cameraPos = new Vector3d(pos.x, pos.y, pos.z);
        }

        if (spec.cameraRotation != null) {
            cameraRot = new Quaternionf(
                    spec.cameraRotation.x,
                    spec.cameraRotation.y,
                    spec.cameraRotation.z,
                    spec.cameraRotation.w
            );
        } else if (spec.cameraLookAt != null) {
            // calculate rotation from lookAt
            float dirX = spec.cameraLookAt.x - (float) cameraPos.x;
            float dirY = spec.cameraLookAt.y - (float) cameraPos.y;
            float dirZ = spec.cameraLookAt.z - (float) cameraPos.z;
            float lenSq = dirX * dirX + dirY * dirY + dirZ * dirZ;
            if (lenSq <= 1.0e-6f) {
                cameraRot = new Quaternionf(context.camera.getRotation());
            } else {
                float invLen = 1.0f / (float) Math.sqrt(lenSq);
                dirX *= invLen;
                dirY *= invLen;
                dirZ *= invLen;

                float upX = 0.0f, upY = 1.0f, upZ = 0.0f;
                if (spec.cameraUp != null) {
                    upX = spec.cameraUp.x;
                    upY = spec.cameraUp.y;
                    upZ = spec.cameraUp.z;
                    float upLenSq = upX * upX + upY * upY + upZ * upZ;
                    if (upLenSq > 1.0e-6f) {
                        float upInvLen = 1.0f / (float) Math.sqrt(upLenSq);
                        upX *= upInvLen;
                        upY *= upInvLen;
                        upZ *= upInvLen;
                    } else {
                        upX = 0.0f;
                        upY = 1.0f;
                        upZ = 0.0f;
                    }
                }

                // if up is parallel to direction, pick a different up
                float dot = dirX * upX + dirY * upY + dirZ * upZ;
                if (Math.abs(dot) > 0.999f) {
                    if (Math.abs(dirY) < 0.999f) {
                        upX = 0.0f;
                        upY = 1.0f;
                        upZ = 0.0f;
                    } else {
                        upX = 1.0f;
                        upY = 0.0f;
                        upZ = 0.0f;
                    }
                }

                cameraRot = new Quaternionf().lookAlong(dirX, dirY, dirZ, upX, upY, upZ);
            }
        } else {
            cameraRot = new Quaternionf(context.camera.getRotation());
        }

        float fovRadians = spec.fovRadians != null ? spec.fovRadians : context.projectionMatrix.perspectiveFov();
        float nearPlane = spec.nearPlane != null ? spec.nearPlane : 0.05F;
        float farPlane = spec.farPlane != null ? spec.farPlane : Math.max(nearPlane + 1.0F, spec.renderDistance * 4.0F);

        float aspect = (float) out.getWidth() / Math.max(1.0F, (float) out.getHeight());
        Matrix4f projection = new Matrix4f().setPerspective(fovRadians, aspect, nearPlane, farPlane);

        // convert renderDistance from blocks to chunks
        float renderDistanceChunks = Math.max(1.0F, spec.renderDistance / 16.0F);

        FramebufferState state = FramebufferState.capture();
        try {
            if (spec.clearOut) {
                clearFramebuffer(spec.out, out, null);
            }
            VeilLevelPerspectiveRenderer.render(
                    out,
                    new Matrix4f(),
                    projection,
                    cameraPos,
                    cameraRot,
                    renderDistanceChunks,
                    context.deltaTracker,
                    spec.drawLights
            );
            FramebufferTextureExports.update(spec.out, out);
        } finally {
            state.restore();
        }
    }

    private void clearFramebuffer(
            Identifier framebufferId,
            AdvancedFbo framebuffer,
            @Nullable ClearColor explicitClearColor
    ) {
        ClearColor clearColor = explicitClearColor;
        if (clearColor == null) {
            FramebufferSpec spec = managedFramebuffers.get(framebufferId);
            if (spec != null) {
                clearColor = spec.clearColor();
            }
        }

        if (clearColor == null) {
            framebuffer.clear();
            return;
        }
        framebuffer.clear(
                clearColor.r(),
                clearColor.g(),
                clearColor.b(),
                clearColor.a(),
                clearColor.depth(),
                framebuffer.getClearMask()
        );
    }

    private void applyUniformMap(ShaderProgram shader, Map<String, Object> uniforms) {
        if (uniforms == null || uniforms.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : uniforms.entrySet()) {
            try {
                applyUniform(shader, entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LOGGER.debug("[MultiPass] Failed to apply uniform {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyUniform(ShaderProgram shader, String name, Object value) {
        ShaderUniform uniform = shader.getUniform(name);
        if (uniform == null || value == null) {
            return;
        }

        if (value instanceof Number number) {
            if (value instanceof Integer || value instanceof Long) {
                uniform.setInt(number.intValue());
            } else {
                uniform.setFloat(number.floatValue());
            }
            return;
        }

        if (value instanceof Boolean b) {
            uniform.setInt(b ? 1 : 0);
            return;
        }

        if (value instanceof Vector3 vec3) {
            uniform.setVector(vec3.x, vec3.y, vec3.z);
            return;
        }

        if (value instanceof Quaternion quat) {
            uniform.setVector(quat.x, quat.y, quat.z, quat.w);
            return;
        }

        if (value instanceof List<?> list && !list.isEmpty()) {
            applyVectorFromList(uniform, list);
            return;
        }

        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null) {
                    map.put(String.valueOf(k), v);
                }
            });
            applyVectorFromMap(uniform, map);
        }
    }

    private void applyVectorFromList(ShaderUniform uniform, List<?> list) {
        float x = asFloat(list, 0);
        float y = asFloat(list, 1);
        float z = asFloat(list, 2);
        float w = asFloat(list, 3);

        if (list.size() >= 4) {
            uniform.setVector(x, y, z, w);
        } else if (list.size() == 3) {
            uniform.setVector(x, y, z);
        } else if (list.size() == 2) {
            uniform.setVector(x, y);
        } else if (list.size() == 1) {
            uniform.setFloat(x);
        }
    }

    private float asFloat(List<?> list, int index) {
        if (index >= list.size()) {
            return 0.0F;
        }
        Object raw = list.get(index);
        if (raw instanceof Number number) {
            return number.floatValue();
        }
        try {
            return raw != null ? Float.parseFloat(raw.toString()) : 0.0F;
        } catch (Exception e) {
            return 0.0F;
        }
    }

    private void applyVectorFromMap(ShaderUniform uniform, Map<String, Object> map) {
        float x = asFloat(map.getOrDefault("x", map.getOrDefault("r", 0.0F)));
        float y = asFloat(map.getOrDefault("y", map.getOrDefault("g", 0.0F)));
        float z = asFloat(map.getOrDefault("z", map.getOrDefault("b", 0.0F)));

        boolean hasW = map.containsKey("w") || map.containsKey("a");
        if (hasW) {
            float w = asFloat(map.getOrDefault("w", map.getOrDefault("a", 1.0F)));
            uniform.setVector(x, y, z, w);
        } else {
            uniform.setVector(x, y, z);
        }
    }

    private float asFloat(Object raw) {
        if (raw instanceof Number num) {
            return num.floatValue();
        }
        try {
            return raw != null ? Float.parseFloat(raw.toString()) : 0.0F;
        } catch (Exception e) {
            return 0.0F;
        }
    }

    private @Nullable AdvancedFbo getFramebuffer(Identifier id) {
        FramebufferManager framebufferManager = VeilRenderSystem.renderer().getFramebufferManager();
        return framebufferManager.getFramebuffer(id);
    }

    private record RenderContext(Matrix4fc projectionMatrix, RenderTickCounter deltaTracker, Camera camera) {
    }

    private enum PassType {
        BLIT,
        COPY,
        WORLD,
        CLEAR
    }

    private enum SizeMode {
        SCREEN,
        FIXED
    }

    private enum AttachmentType {
        TEXTURE,
        RENDER_BUFFER
    }

    private record SizeSpec(SizeMode mode, int width, int height, float scale) {
        int resolveWidth(int screenWidth) {
            return switch (mode) {
                case FIXED -> Math.max(1, width);
                case SCREEN -> Math.max(1, Math.round(screenWidth * Math.max(0.01F, scale)));
            };
        }

        int resolveHeight(int screenHeight) {
            return switch (mode) {
                case FIXED -> Math.max(1, height);
                case SCREEN -> Math.max(1, Math.round(screenHeight * Math.max(0.01F, scale)));
            };
        }
    }

    private record ClearColor(float r, float g, float b, float a, float depth) {
        static final ClearColor DEFAULT = new ClearColor(0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    }

    private record AttachmentSpec(AttachmentType type,
                                  FramebufferAttachmentDefinition.Format format,
                                  int levels,
                                  boolean linear,
                                  @Nullable String name) {
    }

    private record FramebufferSpec(Identifier id,
                                   SizeSpec size,
                                   boolean autoClear,
                                   ClearColor clearColor,
                                   List<AttachmentSpec> colorAttachments,
                                   @Nullable AttachmentSpec depthAttachment) {

        static FramebufferSpec fromOptions(Identifier id, @Nullable Map<String, Object> options) {
            Map<String, Object> map = options != null ? options : Map.of();

            SizeSpec size = parseSize(map);
            Object autoClearRaw = map.get("autoClear");
            if (autoClearRaw == null) autoClearRaw = map.get("auto_clear");
            boolean autoClear = asBoolean(autoClearRaw, true);
            ClearColor clearColor = parseClearColor(map.get("clearColor"));
            if (clearColor == null) {
                clearColor = ClearColor.DEFAULT;
            }
            List<AttachmentSpec> colorAttachments = parseColorAttachments(map);
            AttachmentSpec depthAttachment = parseDepthAttachment(map.get("depth"));

            return new FramebufferSpec(id, size, autoClear, clearColor, colorAttachments, depthAttachment);
        }

        private static SizeSpec parseSize(Map<String, Object> options) {
            Object widthRaw = options.get("width");
            Object heightRaw = options.get("height");
            Object scaleRaw = options.get("scale");

            if (widthRaw instanceof Number widthNumber && heightRaw instanceof Number heightNumber) {
                return new SizeSpec(
                        SizeMode.FIXED,
                        Math.max(1, widthNumber.intValue()),
                        Math.max(1, heightNumber.intValue()),
                        1.0F
                );
            }

            float scale = 1.0F;
            if (scaleRaw instanceof Number num) {
                scale = num.floatValue();
            } else if (scaleRaw != null) {
                try {
                    scale = Float.parseFloat(scaleRaw.toString());
                } catch (Exception ignored) {
                    scale = 1.0F;
                }
            }
            return new SizeSpec(SizeMode.SCREEN, 0, 0, scale);
        }

        private static List<AttachmentSpec> parseColorAttachments(Map<String, Object> options) {
            Object raw = options.get("color_buffers");
            if (raw == null) raw = options.get("colorBuffers");
            if (raw == null) {
                raw = options.get("color");
            }

            List<AttachmentSpec> attachments = new ArrayList<>();
            if (raw instanceof List<?> list) {
                for (Object entry : list) {
                    AttachmentSpec spec = parseAttachment(entry, FramebufferAttachmentDefinition.Format.RGBA8);
                    if (spec != null) {
                        attachments.add(spec);
                    }
                }
            } else {
                AttachmentSpec spec = parseAttachment(raw, FramebufferAttachmentDefinition.Format.RGBA8);
                if (spec != null) {
                    attachments.add(spec);
                }
            }

            if (attachments.isEmpty()) {
                attachments.add(new AttachmentSpec(
                        AttachmentType.TEXTURE,
                        FramebufferAttachmentDefinition.Format.RGBA8,
                        1,
                        false,
                        null
                ));
            }
            return attachments;
        }

        private static @Nullable AttachmentSpec parseDepthAttachment(Object raw) {
            if (raw == null) {
                return null;
            }
            if (raw instanceof Boolean b) {
                if (!b) {
                    return null;
                }
                return new AttachmentSpec(
                        AttachmentType.TEXTURE,
                        FramebufferAttachmentDefinition.Format.DEPTH_COMPONENT,
                        1,
                        false,
                        null
                );
            }
            return parseAttachment(raw, FramebufferAttachmentDefinition.Format.DEPTH_COMPONENT);
        }

        private static @Nullable AttachmentSpec parseAttachment(
                Object raw,
                FramebufferAttachmentDefinition.Format defaultFormat
        ) {
            if (!(raw instanceof Map<?, ?> map)) {
                if (raw == null) {
                    return null;
                }
                if (raw instanceof String s && "true".equalsIgnoreCase(s.trim())) {
                    return new AttachmentSpec(AttachmentType.TEXTURE, defaultFormat, 1, false, null);
                }
                return null;
            }

            String typeRaw = asString(map.get("type"), "texture");
            AttachmentType type = "render_buffer".equalsIgnoreCase(typeRaw) || "renderbuffer".equalsIgnoreCase(typeRaw)
                    ? AttachmentType.RENDER_BUFFER
                    : AttachmentType.TEXTURE;

            String formatRaw = asString(map.get("format"), defaultFormat.name());
            FramebufferAttachmentDefinition.Format format = parseFormat(formatRaw, defaultFormat);

            int levels = asInt(map.get("levels"), 1);
            boolean linear = asBoolean(map.get("linear"), false);
            String name = asNullableString(map.get("name"));

            return new AttachmentSpec(type, format, Math.max(1, levels), linear, name);
        }

        private static FramebufferAttachmentDefinition.Format parseFormat(
                String raw,
                FramebufferAttachmentDefinition.Format fallback
        ) {
            if (raw == null) {
                return fallback;
            }
            String candidate = raw.trim().toUpperCase(Locale.ROOT);
            try {
                return FramebufferAttachmentDefinition.Format.valueOf(candidate);
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private static @Nullable ClearColor parseClearColor(Object raw) {
            if (!(raw instanceof Map<?, ?> map)) {
                return null;
            }

            Object rRaw = map.get("r");
            if (rRaw == null) {
                rRaw = map.get("x");
            }
            Object gRaw = map.get("g");
            if (gRaw == null) {
                gRaw = map.get("y");
            }
            Object bRaw = map.get("b");
            if (bRaw == null) {
                bRaw = map.get("z");
            }
            Object aRaw = map.get("a");
            if (aRaw == null) {
                aRaw = map.get("w");
            }

            float r = asFloat(rRaw, 0.0F);
            float g = asFloat(gRaw, 0.0F);
            float b = asFloat(bRaw, 0.0F);
            float a = asFloat(aRaw, 0.0F);
            float depth = asFloat(map.get("depth"), 1.0F);
            return new ClearColor(r, g, b, a, depth);
        }
    }

    private static final class RenderPassSpec {
        private final PassType type;
        private final String stage;
        private final int order;
        private final boolean enabled;
        private final Object payload;

        private RenderPassSpec(PassType type, String stage, int order, boolean enabled, Object payload) {
            this.type = type;
            this.stage = stage;
            this.order = order;
            this.enabled = enabled;
            this.payload = payload;
        }

        static @Nullable RenderPassSpec fromOptions(String passId, @Nullable Map<String, Object> options) {
            if (options == null) {
                LOGGER.warn("[MultiPass] Pass '{}' missing options", passId);
                return null;
            }

            String typeRaw = asString(options.get("type"), null);
            if (typeRaw == null) {
                LOGGER.warn("[MultiPass] Pass '{}' missing type", passId);
                return null;
            }

            PassType type;
            try {
                type = PassType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                LOGGER.warn("[MultiPass] Pass '{}' has unknown type: {}", passId, typeRaw);
                return null;
            }

            String stage = normalizeStage(asString(options.getOrDefault("stage", "after_level"), "after_level"));
            int order = asInt(options.getOrDefault("order", 0), 0);
            boolean enabled = asBoolean(options.getOrDefault("enabled", true), true);

            return switch (type) {
                case COPY -> {
                    CopyPassSpec spec = CopyPassSpec.fromOptions(options);
                    yield spec != null ? new RenderPassSpec(type, stage, order, enabled, spec) : null;
                }
                case BLIT -> {
                    BlitPassSpec spec = BlitPassSpec.fromOptions(options);
                    yield spec != null ? new RenderPassSpec(type, stage, order, enabled, spec) : null;
                }
                case WORLD -> {
                    WorldPassSpec spec = WorldPassSpec.fromOptions(options);
                    yield spec != null ? new RenderPassSpec(type, stage, order, enabled, spec) : null;
                }
                case CLEAR -> {
                    ClearPassSpec spec = ClearPassSpec.fromOptions(options);
                    yield spec != null ? new RenderPassSpec(type, stage, order, enabled, spec) : null;
                }
            };
        }

        RenderPassSpec withEnabled(boolean enabled) {
            return new RenderPassSpec(this.type, this.stage, this.order, enabled, this.payload);
        }

        CopyPassSpec asCopy() {
            return (CopyPassSpec) payload;
        }

        BlitPassSpec asBlit() {
            return (BlitPassSpec) payload;
        }

        WorldPassSpec asWorld() {
            return (WorldPassSpec) payload;
        }

        ClearPassSpec asClear() {
            return (ClearPassSpec) payload;
        }
    }

    private record CopyPassSpec(Identifier in, Identifier out, boolean color, boolean depth, boolean linear) {
        static @Nullable CopyPassSpec fromOptions(Map<String, Object> options) {
            Identifier in = parseIdentifier(options.getOrDefault("in", MAIN_FRAMEBUFFER.toString()), MAIN_FRAMEBUFFER);
            Identifier out = parseIdentifier(
                    options.getOrDefault("out", MAIN_FRAMEBUFFER.toString()),
                    MAIN_FRAMEBUFFER
            );
            boolean color = asBoolean(options.getOrDefault("color", true), true);
            boolean depth = asBoolean(options.getOrDefault("depth", false), false);
            boolean linear = asBoolean(options.getOrDefault("linear", false), false);

            return new CopyPassSpec(in, out, color, depth, linear);
        }
    }

    private static final class BlitPassSpec {
        private final Identifier shader;
        private final @Nullable Identifier in;
        private final Identifier out;
        private final boolean clearOut;
        private final Map<String, Object> uniforms;
        private boolean printedError;

        private BlitPassSpec(
                Identifier shader,
                @Nullable Identifier in,
                Identifier out,
                boolean clearOut,
                Map<String, Object> uniforms
        ) {
            this.shader = shader;
            this.in = in;
            this.out = out;
            this.clearOut = clearOut;
            this.uniforms = uniforms;
            this.printedError = false;
        }

        static @Nullable BlitPassSpec fromOptions(Map<String, Object> options) {
            Identifier shader = parseIdentifier(options.get("shader"), null);
            if (shader == null) {
                return null;
            }

            Identifier in = parseIdentifier(options.get("in"), null);
            Identifier out = parseIdentifier(
                    options.getOrDefault("out", MAIN_FRAMEBUFFER.toString()),
                    MAIN_FRAMEBUFFER
            );
            boolean clearOut = asBoolean(options.getOrDefault("clear", true), true);
            Map<String, Object> uniforms = parseUniforms(options.get("uniforms"));
            return new BlitPassSpec(shader, in, out, clearOut, uniforms);
        }

        boolean printErrorOnce() {
            if (printedError) {
                return false;
            }
            printedError = true;
            return true;
        }
    }

    private record WorldPassSpec(Identifier out,
                                 @Nullable Vector3 cameraPosition,
                                 @Nullable Quaternion cameraRotation,
                                 @Nullable Vector3 cameraLookAt,
                                 @Nullable Vector3 cameraUp,
                                 @Nullable Float fovRadians,
                                 @Nullable Float nearPlane,
                                 @Nullable Float farPlane,
                                 float renderDistance,
                                 boolean drawLights,
                                 boolean clearOut) {

        static @Nullable WorldPassSpec fromOptions(Map<String, Object> options) {
            Identifier out = parseIdentifier(options.getOrDefault("out", MAIN_FRAMEBUFFER.toString()), null);
            if (out == null) {
                return null;
            }

            Vector3 cameraPosition = null;
            Quaternion cameraRotation = null;
            Vector3 cameraLookAt = null;
            Vector3 cameraUp = null;
            Object cameraRaw = options.get("camera");
            if (cameraRaw instanceof Map<?, ?> cameraMap) {
                cameraPosition = parseVector3(cameraMap.get("position"));
                cameraRotation = parseQuaternion(cameraMap.get("rotation"));
                cameraLookAt = parseVector3(cameraMap.get("lookAt"));
                cameraUp = parseVector3(cameraMap.get("up"));
            }

            Float fovRadians = parseFovRadians(options.get("fov"));
            Float nearPlane = parseOptionalFloat(options.get("near"));
            Float farPlane = parseOptionalFloat(options.get("far"));

            float renderDistance = asFloat(options.getOrDefault("renderDistance", 64.0F), 64.0F);
            boolean drawLights = asBoolean(options.getOrDefault("drawLights", false), false);
            boolean clearOut = asBoolean(options.getOrDefault("clear", true), true);

            return new WorldPassSpec(
                    out,
                    cameraPosition,
                    cameraRotation,
                    cameraLookAt,
                    cameraUp,
                    fovRadians,
                    nearPlane,
                    farPlane,
                    renderDistance,
                    drawLights,
                    clearOut
            );
        }
    }

    private record ClearPassSpec(Identifier target, @Nullable ClearColor clearColor) {
        static @Nullable ClearPassSpec fromOptions(Map<String, Object> options) {
            Identifier target = parseIdentifier(
                    options.getOrDefault("target", options.getOrDefault("out", MAIN_FRAMEBUFFER.toString())),
                    null
            );
            if (target == null) {
                return null;
            }
            ClearColor clearColor = FramebufferSpec.parseClearColor(options.get("clearColor"));
            return new ClearPassSpec(target, clearColor);
        }
    }

    private record FramebufferState(
            int framebuffer,
            int readFramebuffer,
            int drawFramebuffer,
            int viewportX,
            int viewportY,
            int viewportW,
            int viewportH
    ) {
        static FramebufferState capture() {
            RenderSystem.assertOnRenderThread();
            int framebuffer = glGetInteger(GL_FRAMEBUFFER_BINDING);
            int readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            int x;
            int y;
            int w;
            int h;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer buf = stack.mallocInt(4);
                glGetIntegerv(GL_VIEWPORT, buf);
                x = buf.get(0);
                y = buf.get(1);
                w = buf.get(2);
                h = buf.get(3);
            }
            return new FramebufferState(framebuffer, readFramebuffer, drawFramebuffer, x, y, w, h);
        }

        void restore() {
            RenderSystem.assertOnRenderThread();
            if (this.framebuffer == AdvancedFbo.getMainFramebuffer().getId()) {
                AdvancedFbo.unbind();
            } else {
                GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, this.framebuffer);
            }
            GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER, this.readFramebuffer);
            GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
            RenderSystem.viewport(this.viewportX, this.viewportY, this.viewportW, this.viewportH);
        }
    }

    private static String normalizeStage(String stage) {
        if (stage == null) {
            return "after_level";
        }
        String trimmed = stage.trim();
        if (trimmed.isEmpty()) {
            return "after_level";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static @Nullable Identifier parseIdentifier(Object raw, @Nullable Identifier fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Identifier id) {
            return id;
        }
        Identifier parsed = Identifier.tryParse(raw.toString());
        return parsed != null ? parsed : fallback;
    }

    private static @Nullable String asNullableString(Object raw) {
        String value = asString(raw, null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String asString(Object raw, @Nullable String fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof String s) {
            return s;
        }
        return raw.toString();
    }

    private static int asInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean asBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw == null) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.toString());
    }

    private static float asFloat(Object raw, float fallback) {
        if (raw instanceof Number number) {
            return number.floatValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static @Nullable Float parseOptionalFloat(Object raw) {
        if (raw == null) {
            return null;
        }
        return asFloat(raw, 0.0F);
    }

    private static @Nullable Float parseFovRadians(Object raw) {
        if (raw == null) {
            return null;
        }
        float value = asFloat(raw, 0.0F);
        if (value <= 0.0F) {
            return null;
        }

        // treat values > 2π as degrees
        if (value > (float) (Math.PI * 2.0)) {
            return (float) Math.toRadians(value);
        }
        return value;
    }

    private static @Nullable Vector3 parseVector3(Object raw) {
        if (raw instanceof Vector3 v) {
            return v;
        }
        if (raw instanceof Map<?, ?> map) {
            float x = asFloat(map.get("x"), 0.0F);
            float y = asFloat(map.get("y"), 0.0F);
            float z = asFloat(map.get("z"), 0.0F);
            return new Vector3(x, y, z);
        }
        return null;
    }

    private static @Nullable Quaternion parseQuaternion(Object raw) {
        if (raw instanceof Quaternion q) {
            return q;
        }
        if (raw instanceof Map<?, ?> map) {
            float x = asFloat(map.get("x"), 0.0F);
            float y = asFloat(map.get("y"), 0.0F);
            float z = asFloat(map.get("z"), 0.0F);
            float w = asFloat(map.get("w"), 1.0F);
            return new Quaternion(x, y, z, w);
        }
        return null;
    }

    private static Map<String, Object> parseUniforms(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> uniforms = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            uniforms.put(entry.getKey().toString(), entry.getValue());
        }
        return uniforms;
    }
}
