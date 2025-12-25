package com.moud.client.init;

import com.moud.api.math.Quaternion;
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
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.ik.ClientIKManager;
import com.moud.client.model.ClientModelManager;
import com.moud.client.model.ModelRenderer;
import com.moud.client.model.RenderableModel;
import com.moud.client.particle.ParticleRenderer;
import com.moud.client.primitives.ClientPrimitive;
import com.moud.client.primitives.ClientPrimitiveManager;
import com.moud.client.primitives.PrimitiveRenderer;
import com.moud.network.MoudPackets;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joml.Quaternionf;

import java.util.List;

public class ClientRenderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRenderController.class);
    private static final boolean DISABLE_VEIL_BUFFERS = Boolean.getBoolean("moud.disableVeilBuffers");
    private static final boolean DISABLE_VEIL_BLOOM = Boolean.getBoolean("moud.disableVeilBloom");

    private static boolean customCameraActive = false;

    private final ModelRenderer modelRenderer = new ModelRenderer();
    private final DisplayRenderer displayRenderer = new DisplayRenderer();
    private final PlayerModelRenderer playerModelRenderer = new PlayerModelRenderer();
    private final PrimitiveRenderer primitiveRenderer = new PrimitiveRenderer();
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
            renderPrimitives(renderContext, services);
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

            ClientIKManager.getInstance().render(
                    renderContext.matrixStack(),
                    renderContext.consumers(),
                    camera.getPos()
            );
            SceneEditorOverlay.getInstance().renderCameraGizmos(renderContext);
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
        var primitiveBoxes = getPrimitiveDebugBoxes();
        if (boxes.isEmpty() && meshBounds.isEmpty() && tris.isEmpty() && primitiveBoxes.isEmpty()) {
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
        for (Box box : primitiveBoxes) {
            WorldRenderer.drawBox(matrices, buffer, box, 1.0f, 0.2f, 1.0f, 1.0f);
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

    private List<Box> getPrimitiveDebugBoxes() {
        List<Box> boxes = new java.util.ArrayList<>();
        for (ClientPrimitive prim : ClientPrimitiveManager.getInstance().getPrimitives()) {
            if (prim == null || prim.isLineType()) {
                continue;
            }
            Vector3 pos = prim.getInterpolatedPosition(0f);
            Vector3 scale = prim.getInterpolatedScale(0f);
            double hx, hy, hz;
            switch (prim.getType()) {
                case SPHERE -> {
                    double r = Math.max(Math.max(Math.abs(scale.x), Math.abs(scale.y)), Math.abs(scale.z)) * 0.5;
                    hx = hy = hz = r;
                }
                case CYLINDER, CAPSULE, CONE -> {
                    hx = Math.abs(scale.x) * 0.5;
                    hy = Math.abs(scale.y) * 0.5;
                    hz = Math.abs(scale.z) * 0.5;
                }
                case MESH -> {
                    Box meshBounds = computeMeshBounds(prim, pos);
                    if (meshBounds != null) {
                        boxes.add(meshBounds);
                    }
                    continue;
                }
                default -> { // CUBE, PLANE
                    hx = Math.abs(scale.x) * 0.5;
                    hy = Math.abs(scale.y) * 0.5;
                    hz = Math.abs(scale.z) * 0.5;
                }
            }
            boxes.add(new Box(pos.x - hx, pos.y - hy, pos.z - hz,
                    pos.x + hx, pos.y + hy, pos.z + hz));
        }
        return boxes;
    }

    private Box computeMeshBounds(ClientPrimitive prim, Vector3 pos) {
        java.util.List<Vector3> verts = prim.getVertices();
        if (verts == null || verts.isEmpty()) {
            return null;
        }
        Vector3 scale = prim.getInterpolatedScale(0f);
        com.moud.api.math.Quaternion rot = prim.getInterpolatedRotation(0f);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vector3 v : verts) {
            double sx = v.x * scale.x;
            double sy = v.y * scale.y;
            double sz = v.z * scale.z;
            double wx = sx + pos.x;
            double wy = sy + pos.y;
            double wz = sz + pos.z;
            minX = Math.min(minX, wx);
            minY = Math.min(minY, wy);
            minZ = Math.min(minZ, wz);
            maxX = Math.max(maxX, wx);
            maxY = Math.max(maxY, wy);
            maxZ = Math.max(maxZ, wz);
        }
        if (minX > maxX) {
            return null;
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
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

    private void renderPrimitives(WorldRenderContext renderContext, ClientServiceManager services) {
        if (ClientPrimitiveManager.getInstance().isEmpty()) {
            return;
        }
        var consumers = renderContext.consumers();
        var world = MinecraftClient.getInstance().world;
        if (consumers == null || world == null) {
            return;
        }

        float tickDelta = renderContext.tickCounter().getTickDelta(true);
        Vec3d cameraPos = renderContext.camera().getPos();
        MatrixStack matrices = renderContext.matrixStack();

        for (ClientPrimitive primitive : ClientPrimitiveManager.getInstance().getPrimitives()) {
            if (primitive == null) {
                continue;
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Primitives] Rendering id={} type={} unlit={} xray={}", primitive.getId(),
                        primitive.getType(), primitive.isUnlit(), primitive.isRenderThroughBlocks());
            }
            if (primitive.isLineType()) {
                if (!isVisible(createLineBounds(primitive), renderContext)) {
                    continue;
                }
                matrices.push();
                matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                int light = primitive.isUnlit() || primitive.isRenderThroughBlocks()
                        ? 0xF000F0
                        : WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(cameraPos));
                primitiveRenderer.renderLines(primitive, matrices, consumers, primitive.isRenderThroughBlocks(), light);
                matrices.pop();
                continue;
            }
            Vector3 interpolatedPos = primitive.getInterpolatedPosition(tickDelta);
            var interpolatedRot = primitive.getInterpolatedRotation(tickDelta);
            Vector3 scale = primitive.getInterpolatedScale(tickDelta);
            if (!isVisible(createPrimitiveBounds(primitive, interpolatedPos, interpolatedRot, scale), renderContext)) {
                continue;
            }
            Quaternionf rotation = new Quaternionf(interpolatedRot.x, interpolatedRot.y, interpolatedRot.z, interpolatedRot.w);

            matrices.push();
            matrices.translate(interpolatedPos.x - cameraPos.x, interpolatedPos.y - cameraPos.y, interpolatedPos.z - cameraPos.z);
            matrices.multiply(rotation);
            matrices.scale(scale.x, scale.y, scale.z);

            int light = primitive.isUnlit() || primitive.isRenderThroughBlocks()
                    ? 0xF000F0
                    : WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(interpolatedPos.x, interpolatedPos.y, interpolatedPos.z));

            primitiveRenderer.renderSolid(primitive, matrices, consumers, light);
            matrices.pop();
        }
    }

    private Box createLineBounds(ClientPrimitive primitive) {
        java.util.List<Vector3> verts = primitive != null ? primitive.getVertices() : null;
        if (verts == null || verts.isEmpty()) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (Vector3 v : verts) {
            if (v == null) {
                continue;
            }
            found = true;
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }
        if (!found) {
            return null;
        }
        double pad = 0.03125;
        return new Box(minX - pad, minY - pad, minZ - pad, maxX + pad, maxY + pad, maxZ + pad);
    }

    private Box createPrimitiveBounds(ClientPrimitive primitive, Vector3 position, Quaternion rotation, Vector3 scale) {
        if (primitive == null || position == null || rotation == null || scale == null) {
            return null;
        }
        MoudPackets.PrimitiveType type = primitive.getType();
        if (type == MoudPackets.PrimitiveType.MESH) {
            ClientPrimitive.MeshBounds meshBounds = primitive.getMeshBounds();
            return meshBounds != null ? createMeshBounds(position, rotation, scale, meshBounds) : null;
        }

        double baseHalfX = 0.5;
        double baseHalfY = 0.5;
        double baseHalfZ = 0.5;
        if (type == MoudPackets.PrimitiveType.PLANE) {
            baseHalfY = 0.03125;
        } else if (type == MoudPackets.PrimitiveType.CAPSULE) {
            baseHalfY = 1.0;
        }

        double halfX = Math.max(0.03125, Math.abs(scale.x) * baseHalfX);
        double halfY = Math.max(0.03125, Math.abs(scale.y) * baseHalfY);
        double halfZ = Math.max(0.03125, Math.abs(scale.z) * baseHalfZ);
        return createOrientedBounds(position.x, position.y, position.z, rotation, halfX, halfY, halfZ);
    }

    private Box createMeshBounds(Vector3 position, Quaternion rotation, Vector3 scale, ClientPrimitive.MeshBounds meshBounds) {
        double minX = meshBounds.minX();
        double minY = meshBounds.minY();
        double minZ = meshBounds.minZ();
        double maxX = meshBounds.maxX();
        double maxY = meshBounds.maxY();
        double maxZ = meshBounds.maxZ();

        double localCenterX = (minX + maxX) * 0.5;
        double localCenterY = (minY + maxY) * 0.5;
        double localCenterZ = (minZ + maxZ) * 0.5;
        double localHalfX = (maxX - minX) * 0.5;
        double localHalfY = (maxY - minY) * 0.5;
        double localHalfZ = (maxZ - minZ) * 0.5;

        double scaledCenterX = localCenterX * scale.x;
        double scaledCenterY = localCenterY * scale.y;
        double scaledCenterZ = localCenterZ * scale.z;

        double qx = rotation.x;
        double qy = rotation.y;
        double qz = rotation.z;
        double qw = rotation.w;
        double tx = 2.0 * (qy * scaledCenterZ - qz * scaledCenterY);
        double ty = 2.0 * (qz * scaledCenterX - qx * scaledCenterZ);
        double tz = 2.0 * (qx * scaledCenterY - qy * scaledCenterX);
        double rotatedCenterX = scaledCenterX + qw * tx + (qy * tz - qz * ty);
        double rotatedCenterY = scaledCenterY + qw * ty + (qz * tx - qx * tz);
        double rotatedCenterZ = scaledCenterZ + qw * tz + (qx * ty - qy * tx);

        double centerX = position.x + rotatedCenterX;
        double centerY = position.y + rotatedCenterY;
        double centerZ = position.z + rotatedCenterZ;

        double halfX = Math.max(0.03125, localHalfX * Math.abs(scale.x));
        double halfY = Math.max(0.03125, localHalfY * Math.abs(scale.y));
        double halfZ = Math.max(0.03125, localHalfZ * Math.abs(scale.z));

        return createOrientedBounds(centerX, centerY, centerZ, rotation, halfX, halfY, halfZ);
    }

    private Box createOrientedBounds(double centerX, double centerY, double centerZ, Quaternion rotation,
                                     double halfX, double halfY, double halfZ) {
        double x = rotation.x;
        double y = rotation.y;
        double z = rotation.z;
        double w = rotation.w;

        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yz = y * z;
        double wx = w * x;
        double wy = w * y;
        double wz = w * z;

        double m00 = 1.0 - 2.0 * (yy + zz);
        double m01 = 2.0 * (xy - wz);
        double m02 = 2.0 * (xz + wy);
        double m10 = 2.0 * (xy + wz);
        double m11 = 1.0 - 2.0 * (xx + zz);
        double m12 = 2.0 * (yz - wx);
        double m20 = 2.0 * (xz - wy);
        double m21 = 2.0 * (yz + wx);
        double m22 = 1.0 - 2.0 * (xx + yy);

        double worldHalfX = Math.abs(m00) * halfX + Math.abs(m01) * halfY + Math.abs(m02) * halfZ;
        double worldHalfY = Math.abs(m10) * halfX + Math.abs(m11) * halfY + Math.abs(m12) * halfZ;
        double worldHalfZ = Math.abs(m20) * halfX + Math.abs(m21) * halfY + Math.abs(m22) * halfZ;

        return new Box(centerX - worldHalfX, centerY - worldHalfY, centerZ - worldHalfZ,
                centerX + worldHalfX, centerY + worldHalfY, centerZ + worldHalfZ);
    }
}
