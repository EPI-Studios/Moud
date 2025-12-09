package com.moud.client.init;

import com.moud.api.math.Vector3;
import com.moud.client.animation.AnimatedPlayerModel;
import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.client.animation.PlayerModelRenderer;
import com.moud.client.collision.ClientCollisionManager;
import com.moud.client.collision.ModelCollisionManager;
import com.moud.client.collision.Triangle;
import com.moud.client.display.ClientDisplayManager;
import com.moud.client.display.DisplayRenderer;
import com.moud.client.display.DisplaySurface;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.ModelRenderer;
import com.moud.client.model.RenderableModel;
import com.moud.client.particle.ParticleRenderer;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientRenderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRenderController.class);
    private static final boolean DISABLE_VEIL_BUFFERS = Boolean.getBoolean("moud.disableVeilBuffers");
    private static final boolean DISABLE_VEIL_BLOOM = Boolean.getBoolean("moud.disableVeilBloom");

    private static boolean customCameraActive = false;

    private final ModelRenderer modelRenderer = new ModelRenderer();
    private final DisplayRenderer displayRenderer = new DisplayRenderer();
    private final PlayerModelRenderer playerModelRenderer = new PlayerModelRenderer();
    private ParticleRenderer particleRenderer;
    private long particleFrameTimeNs = 0L;
    private boolean veilBuffersEnabled = false;

    public void register(ClientServiceManager services) {
        this.particleRenderer = new ParticleRenderer(services.getParticleSystem());

        WorldRenderEvents.AFTER_ENTITIES.register(renderContext -> render(renderContext, services));
    }

    private void render(WorldRenderContext renderContext, ClientServiceManager services) {
        Camera camera = renderContext.camera();
        var world = MinecraftClient.getInstance().world;
        float tickDelta = renderContext.tickCounter().getTickDelta(true);
        float frameSeconds = computeParticleFrameDeltaSeconds();

        var emitterSystem = services.getParticleEmitterSystem();
        var particleSystem = services.getParticleSystem();
        if (frameSeconds > 0f && emitterSystem != null && particleSystem != null && world != null) {
            emitterSystem.tick(frameSeconds, particleSystem, world, camera.getPos());
            particleSystem.tick(frameSeconds, world);
        } else if (frameSeconds > 0f && particleSystem != null && world != null) {
            particleSystem.tick(frameSeconds, world);
        }

        VeilRenderer veilRenderer = VeilRenderSystem.renderer();
        boolean enabledBuffers = false;
        if (veilRenderer != null && !DISABLE_VEIL_BUFFERS && !veilBuffersEnabled) {
            try {
                enabledBuffers = veilRenderer.enableBuffers(VeilRenderer.COMPOSITE,
                        DynamicBufferType.ALBEDO,
                        DynamicBufferType.NORMAL);
                if (DISABLE_VEIL_BLOOM) {
                    // intentionally left blank to preserve existing behavior while skipping bloom toggle
                }
                veilBuffersEnabled = enabledBuffers;
            } catch (Throwable t) {
                LOGGER.debug("Failed to enable Veil dynamic buffers", t);
            }
        } else if (veilBuffersEnabled) {
            enabledBuffers = true;
        }

        try {
            if (playerModelRenderer != null && !ClientPlayerModelManager.getInstance().getModels().isEmpty()) {

                for (AnimatedPlayerModel model : ClientPlayerModelManager.getInstance().getModels()) {
                    double x = model.getInterpolatedX(tickDelta) - camera.getPos().getX();
                    double y = model.getInterpolatedY(tickDelta) - camera.getPos().getY();
                    double z = model.getInterpolatedZ(tickDelta) - camera.getPos().getZ();

                    MatrixStack matrices = new MatrixStack();
                    matrices.translate(x, y, z);

                    int light = WorldRenderer.getLightmapCoordinates(world, model.getBlockPos());

                    playerModelRenderer.render(model, matrices, renderContext.consumers(), light, tickDelta);
                }
            }
            Vec3d cameraPos = camera.getPos();

            if (modelRenderer != null && !ClientModelManager.getInstance().getModels().isEmpty()) {
                MatrixStack matrices = renderContext.matrixStack();
                var consumers = renderContext.consumers();
                if (consumers != null) {
                    for (RenderableModel model : ClientModelManager.getInstance().getModels()) {
                        Vector3 interpolatedPos = model.getInterpolatedPosition(tickDelta);
                        if (!isModelVisible(model, interpolatedPos, renderContext)) {
                            continue;
                        }
                        double dx = interpolatedPos.x - cameraPos.x;
                        double dy = interpolatedPos.y - cameraPos.y;
                        double dz = interpolatedPos.z - cameraPos.z;

                        matrices.push();
                        matrices.translate(dx, dy, dz);

                        int light = WorldRenderer.getLightmapCoordinates(world, model.getBlockPos());
                        modelRenderer.render(model, matrices, consumers, light, tickDelta);
                        matrices.pop();
                    }
                }
            }
            if (displayRenderer != null && !ClientDisplayManager.getInstance().isEmpty()) {
                MatrixStack matrices = renderContext.matrixStack();
                var consumers = renderContext.consumers();
                if (consumers != null) {
                    for (DisplaySurface surface : ClientDisplayManager.getInstance().getDisplays()) {
                        Vector3 interpolatedPos = surface.getInterpolatedPosition(tickDelta);
                        if (!isDisplayVisible(surface, interpolatedPos, renderContext)) {
                            continue;
                        }
                        double dx = interpolatedPos.x - cameraPos.x;
                        double dy = interpolatedPos.y - cameraPos.y;
                        double dz = interpolatedPos.z - cameraPos.z;

                        matrices.push();
                        matrices.translate(dx, dy, dz);

                        int light = WorldRenderer.getLightmapCoordinates(world, surface.getBlockPos());
                        displayRenderer.render(surface, matrices, consumers, light, tickDelta);
                        matrices.pop();
                    }
                }
            }
            if (particleRenderer != null && renderContext.consumers() != null) {
                MatrixStack matrices = renderContext.matrixStack();
                particleRenderer.render(matrices, renderContext.consumers(), camera, tickDelta, renderContext.frustum());
            }
            renderModelCollisionHitboxes(renderContext);
            if (services.getCursorManager() != null) {
                services.getCursorManager().render(
                        renderContext.matrixStack(),
                        renderContext.consumers(),
                        renderContext.tickCounter().getTickDelta(true)
                );
            }
            com.moud.client.editor.ui.SceneEditorOverlay.getInstance().renderCameraGizmos(renderContext);
        } finally {
            if (enabledBuffers && veilRenderer != null) {
                try {
                    veilRenderer.disableBuffers(VeilRenderer.COMPOSITE,
                            DynamicBufferType.ALBEDO,
                            DynamicBufferType.NORMAL);
                } catch (Throwable t) {
                    LOGGER.debug("Failed to disable Veil dynamic buffers", t);
                }
            }
        }
    }

    private float computeParticleFrameDeltaSeconds() {
        long now = System.nanoTime();
        if (particleFrameTimeNs == 0L) {
            particleFrameTimeNs = now;
            return 0f;
        }
        float dt = (now - particleFrameTimeNs) / 1_000_000_000f;
        particleFrameTimeNs = now;
        return dt;
    }

    private boolean isModelVisible(RenderableModel model, Vector3 position, WorldRenderContext context) {
        return isVisible(createModelBounds(model, position), context);
    }

    private boolean isDisplayVisible(DisplaySurface surface, Vector3 position, WorldRenderContext context) {
        return isVisible(createDisplayBounds(surface, position), context);
    }

    private boolean isVisible(Box bounds, WorldRenderContext context) {
        if (bounds == null) {
            return true;
        }
        try {
            var frustum = context.frustum();
            if (frustum == null) {
                return true;
            }
            if (frustum instanceof CullFrustum cullFrustum) {
                return cullFrustum.testAab(bounds.minX, bounds.minY, bounds.minZ,
                        bounds.maxX, bounds.maxY, bounds.maxZ);
            }
            return frustum.isVisible(bounds);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void renderModelCollisionHitboxes(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        var dispatcher = client.getEntityRenderDispatcher();
        if (dispatcher == null || !dispatcher.shouldRenderHitboxes()) {
            return;
        }
        var boxes = ModelCollisionManager.getInstance().getDebugBoxes();
        var meshBounds = ClientCollisionManager.getDebugMeshBounds();
        var tris = ClientCollisionManager.getDebugTriangles();
        if (boxes.isEmpty() && meshBounds.isEmpty() && tris.isEmpty()) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        if (buffer == null) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (Box box : boxes) {
            WorldRenderer.drawBox(matrices, buffer, box, 1.0f, 0.2f, 0.2f, 1.0f);
        }
        if (!tris.isEmpty()) {
            for (Triangle tri : tris) {
                drawLine(buffer, matrices, tri.v0, tri.v1, 0.1f, 0.6f, 1.0f, 1.0f);
                drawLine(buffer, matrices, tri.v1, tri.v2, 0.1f, 0.6f, 1.0f, 1.0f);
                drawLine(buffer, matrices, tri.v2, tri.v0, 0.1f, 0.6f, 1.0f, 1.0f);
            }
        } else if (!meshBounds.isEmpty()) {
            for (Box meshBound : meshBounds) {
                WorldRenderer.drawBox(matrices, buffer, meshBound, 0.1f, 0.8f, 0.1f, 1.0f);
            }
        } else {
            for (var mesh : ClientCollisionManager.getAllMeshes()) {
                if (mesh.getBounds() != null) {
                    WorldRenderer.drawBox(matrices, buffer, mesh.getBounds(), 0.1f, 0.6f, 1.0f, 1.0f);
                }
            }
        }
        matrices.pop();
    }

    private static void drawLine(VertexConsumer buffer, MatrixStack matrices, Vec3d a, Vec3d b,
                                 float r, float g, float bCol, float aCol) {
        var entry = matrices.peek();
        buffer.vertex(entry.getPositionMatrix(), (float) a.x, (float) a.y, (float) a.z)
                .color(r, g, bCol, aCol)
                .normal(entry, 0.0f, 1.0f, 0.0f);

        buffer.vertex(entry.getPositionMatrix(), (float) b.x, (float) b.y, (float) b.z)
                .color(r, g, bCol, aCol)
                .normal(entry, 0.0f, 1.0f, 0.0f);

    }

    private Box createModelBounds(RenderableModel model, Vector3 position) {
        if (model == null || position == null) {
            return null;
        }
        Vector3 scale = model.getScale();
        Vector3 meshHalf = model.getMeshHalfExtents();
        double halfX = computeHalfExtent(meshHalf != null ? meshHalf.x : Double.NaN, scale.x, model.getCollisionWidth());
        double halfY = computeHalfExtent(meshHalf != null ? meshHalf.y : Double.NaN, scale.y, model.getCollisionHeight());
        double halfZ = computeHalfExtent(meshHalf != null ? meshHalf.z : Double.NaN, scale.z, model.getCollisionDepth());
        return new Box(position.x - halfX, position.y - halfY, position.z - halfZ,
                position.x + halfX, position.y + halfY, position.z + halfZ);
    }

    private Box createDisplayBounds(DisplaySurface surface, Vector3 position) {
        if (surface == null || position == null) {
            return null;
        }
        Vector3 scale = surface.getScale();
        double halfX = Math.max(0.25, Math.abs(scale.x) * 0.5);
        double halfY = Math.max(0.25, Math.abs(scale.y) * 0.5);
        double halfZ = Math.max(0.0625, Math.abs(scale.z) * 0.5);
        return new Box(position.x - halfX, position.y - halfY, position.z - halfZ,
                position.x + halfX, position.y + halfY, position.z + halfZ);
    }

    private double computeHalfExtent(double meshValue, double scaleAxis, double collisionSize) {
        double base = !Double.isNaN(meshValue) && meshValue > 0 ? meshValue :
                (collisionSize > 0 ? collisionSize / 2.0 : 0.5);
        double scaleAbs = Math.abs(scaleAxis);
        if (scaleAbs < 1.0e-3) {
            scaleAbs = 1.0;
        }
        return Math.max(0.25, base * scaleAbs);
    }

    public static boolean isCustomCameraActive() {
        return customCameraActive;
    }

    public static void setCustomCameraActive(boolean active) {
        customCameraActive = active;
    }

    public void resetFrameTime() {
        particleFrameTimeNs = 0L;
    }
}
