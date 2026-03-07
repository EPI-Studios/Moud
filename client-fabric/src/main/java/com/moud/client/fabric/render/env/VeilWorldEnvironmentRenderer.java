package com.moud.client.fabric.render.env;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.fabric.env.WorldEnvironmentClient;
import com.moud.client.fabric.env.WorldEnvironmentClient.EnvSettings;
import com.moud.client.fabric.env.WorldEnvironmentClient.Mode;
import com.moud.client.fabric.render.veil.VeilDynamicShaders;
import com.moud.client.fabric.render.veil.VeilMaterialBinding;
import com.moud.client.fabric.mixin.accessor.GameRendererAccessor;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.fabric.event.FabricVeilRenderLevelStageEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4fc;

public final class VeilWorldEnvironmentRenderer {
    private static boolean initialized;

    private static final VeilMaterialBinding skyBinding = new VeilMaterialBinding();
    private static final VeilMaterialBinding cloudsBinding = new VeilMaterialBinding();

    private VeilWorldEnvironmentRenderer() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        FabricVeilRenderLevelStageEvent.EVENT.register(VeilWorldEnvironmentRenderer::onRenderLevelStage);
    }

    public static void clear() {
        skyBinding.clear();
        cloudsBinding.clear();
        VeilDynamicShaders.clear();
    }

    private static void onRenderLevelStage(VeilRenderLevelStageEvent.Stage stage,
                                           WorldRenderer levelRenderer,
                                           VertexConsumerProvider.Immediate bufferSource,
                                           foundry.veil.api.client.render.MatrixStack matrixStack,
                                           Matrix4fc frustumMatrix,
                                           Matrix4fc projectionMatrix,
                                           int renderTick,
                                           RenderTickCounter deltaTracker,
                                           Camera camera,
                                           Frustum frustum) {
        EnvSettings env = WorldEnvironmentClient.current();
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_SKY) {
            renderSky(env, deltaTracker, camera, frustumMatrix);
            // Clouds at AFTER_SKY so 3D geometry renders over them (correct occlusion).
            renderClouds(env, deltaTracker, camera, frustumMatrix);
        }
    }

    private static void renderSky(EnvSettings env, RenderTickCounter deltaTracker, Camera camera, Matrix4fc frustumMatrix) {
        if (env.skyMode() != Mode.CUSTOM) {
            return;
        }
        if (!skyBinding.configure(env.skyMaterial(), env.skyShader())) {
            return;
        }
        ShaderProgram program = skyBinding.resolveProgram();
        if (program == null) {
            return;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        try {
            VeilRenderSystem.setShader(program);
            program.bind();
            program.setDefaultUniforms(VertexFormat.DrawMode.TRIANGLE_STRIP);
            applyCommonUniforms(program, deltaTracker, camera, frustumMatrix);
            applySkyUniforms(program, env);
            skyBinding.applyMaterial(program);
            program.bindSamplers(0);
            VeilRenderSystem.drawScreenQuad();
        } finally {
            ShaderProgram.unbind();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void renderClouds(EnvSettings env, RenderTickCounter deltaTracker, Camera camera, Matrix4fc frustumMatrix) {
        if (env.cloudsMode() != Mode.CUSTOM) {
            return;
        }
        if (!cloudsBinding.configure(env.cloudsMaterial(), env.cloudsShader())) {
            return;
        }
        ShaderProgram program = cloudsBinding.resolveProgram();
        if (program == null) {
            return;
        }

        // Render before geometry (called at AFTER_SKY) so depth testing is not needed:
        // the subsequent opaque geometry pass will naturally overdraw cloud pixels.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        try {
            VeilRenderSystem.setShader(program);
            program.bind();
            program.setDefaultUniforms(VertexFormat.DrawMode.TRIANGLE_STRIP);
            applyCommonUniforms(program, deltaTracker, camera, frustumMatrix);
            applyCloudUniforms(program, env, deltaTracker);
            cloudsBinding.applyMaterial(program);
            program.bindSamplers(0);
            VeilRenderSystem.drawScreenQuad();
        } finally {
            ShaderProgram.unbind();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void applyCommonUniforms(ShaderProgram program, RenderTickCounter deltaTracker, Camera camera, Matrix4fc frustumMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (client.world != null) {
            int timeTicks = (int) (client.world.getTimeOfDay() % 24_000L);
            setIntIfPresent(program, "moud_timeTicks", timeTicks);
            setFloatIfPresent(program, "moud_time01", (float) (timeTicks / 24_000.0));
        }
        if (camera != null) {
            Vec3d p = camera.getPos();
            setVec3IfPresent(program, "moud_cameraPos", (float) p.x, (float) p.y, (float) p.z);

            setFloatIfPresent(program, "moud_camYaw",   camera.getYaw());
            setFloatIfPresent(program, "moud_camPitch", camera.getPitch());
        }
        if (frustumMatrix != null) {
            // Extract world-space camera basis vectors directly from the view matrix.
            // The view matrix columns encode: col0=right, col1=up, col2=-forward (OpenGL: camera looks toward -Z).
            // This is the ground-truth orientation used by Minecraft's renderer, with no angle-convention ambiguity.
            float rgtX = frustumMatrix.m00();
            float rgtY = frustumMatrix.m01();
            float rgtZ = frustumMatrix.m02();

            float upX  = frustumMatrix.m10();
            float upY  = frustumMatrix.m11();
            float upZ  = frustumMatrix.m12();

            // Column 2 points toward +Z in camera space = away from viewer; negate to get world-space forward.
            float fwdX = -frustumMatrix.m20();
            float fwdY = -frustumMatrix.m21();
            float fwdZ = -frustumMatrix.m22();

            setVec3IfPresent(program, "moud_camForward", fwdX, fwdY, fwdZ);
            setVec3IfPresent(program, "moud_camRight",   rgtX, rgtY, rgtZ);
            setVec3IfPresent(program, "moud_camUp",      upX,  upY,  upZ);
        }
        if (client.gameRenderer != null) {
            try {
                float fov = (float) ((GameRendererAccessor) client.gameRenderer).moud$getFov(camera, tickDelta(deltaTracker), true);
                setFloatIfPresent(program, "moud_fovDeg", fov);
                if (client.getWindow() != null) {
                    float aspect = (float) client.getWindow().getFramebufferWidth()
                            / (float) Math.max(1, client.getWindow().getFramebufferHeight());
                    setFloatIfPresent(program, "moud_aspectRatio", aspect);
                }
            } catch (Exception ignored) {
            }
        }
        float dt = tickDelta(deltaTracker);
        setFloatIfPresent(program, "moud_tickDelta", dt);
    }

    private static void applySkyUniforms(ShaderProgram program, EnvSettings env) {
        setVec3IfPresent(program, "moud_skyTopColor", env.skyTopR(), env.skyTopG(), env.skyTopB());
        setVec3IfPresent(program, "moud_skyHorizonColor", env.skyHorizonR(), env.skyHorizonG(), env.skyHorizonB());
        setVec3IfPresent(program, "moud_skySunriseColor", env.skySunriseR(), env.skySunriseG(), env.skySunriseB());
        setFloatIfPresent(program, "moud_skySunriseStrength", env.skySunriseStrength());
    }

    private static void applyCloudUniforms(ShaderProgram program, EnvSettings env, RenderTickCounter deltaTracker) {
        setFloatIfPresent(program, "moud_cloudHeight", env.cloudHeight());
        setFloatIfPresent(program, "moud_cloudScale", env.cloudScale());
        setFloatIfPresent(program, "moud_cloudSpeed", env.cloudSpeed());

        MinecraftClient client = MinecraftClient.getInstance();
        float t = 0.0f;
        if (client != null && client.world != null) {
            t = (float) ((client.world.getTime() + tickDelta(deltaTracker)) / 20.0);
        }
        float ox = env.cloudOffsetX() + env.cloudSpeed() * t;
        float oz = env.cloudOffsetZ() + env.cloudSpeed() * t;
        setVec2IfPresent(program, "moud_cloudOffset", ox, oz);
        setFloatIfPresent(program, "moud_cloudTime", t);
    }

    private static float tickDelta(RenderTickCounter deltaTracker) {
        if (deltaTracker == null) {
            return 0.0f;
        }
        try {
            return deltaTracker.getTickDelta(true);
        } catch (Exception ignored) {
            return 0.0f;
        }
    }

    private static void setFloatIfPresent(ShaderProgram program, String uniform, float v) {
        if (!Float.isFinite(v) || !program.hasUniform(uniform)) {
            return;
        }
        program.getUniformSafe(uniform).setFloat(v);
    }

    private static void setIntIfPresent(ShaderProgram program, String uniform, int v) {
        if (!program.hasUniform(uniform)) {
            return;
        }
        program.getUniformSafe(uniform).setInt(v);
    }

    private static void setVec2IfPresent(ShaderProgram program, String uniform, float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !program.hasUniform(uniform)) {
            return;
        }
        program.getUniformSafe(uniform).setVector(x, y);
    }

    private static void setVec3IfPresent(ShaderProgram program, String uniform, float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !program.hasUniform(uniform)) {
            return;
        }
        program.getUniformSafe(uniform).setVector(x, y, z);
    }

    // Uses VeilMaterialBinding for dynamic shader compilation + param binding.
}
