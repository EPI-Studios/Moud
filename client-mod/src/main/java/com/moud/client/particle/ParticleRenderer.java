package com.moud.client.particle;

import com.moud.api.particle.RenderType;
import com.moud.api.particle.Billboarding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

public final class ParticleRenderer {
    private static final int MAX_PARTICLES_PER_FRAME = 8192;

    private final ParticleSystem system;
    private final Map<RenderType, RenderLayerFactory> renderLayers = new EnumMap<>(RenderType.class);
    private final Map<String, Identifier> textureCache = new HashMap<>();

    private static final float PI = (float) Math.PI;
    private static final float HALF_PI = PI * 0.5f;
    private static final float TWO_PI = PI * 2.0f;

    public ParticleRenderer(ParticleSystem system) {
        this.system = system;
        renderLayers.put(RenderType.TRANSLUCENT, RenderLayer::getEntityTranslucent);
        renderLayers.put(RenderType.ADDITIVE, RenderLayer::getEntityTranslucentEmissive);
        renderLayers.put(RenderType.CUTOUT, RenderLayer::getEntityCutout);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, float tickDelta, net.minecraft.client.render.Frustum frustum) {
        int count = Math.min(system.getActiveCount(), MAX_PARTICLES_PER_FRAME);
        if (count == 0) {
            return;
        }

        ParticleInstance[] pool = system.getParticles();
        int[] active = system.getActiveIndices();
        int activeCount = Math.min(system.getActiveCount(), MAX_PARTICLES_PER_FRAME);

        Vec3d camPos = camera.getPos();

        Map<RenderType, Map<Identifier, java.util.List<ParticleInstance>>> buckets = new EnumMap<>(RenderType.class);
        for (RenderType type : RenderType.values()) {
            buckets.put(type, new HashMap<>());
        }
        for (int i = 0; i < activeCount; i++) {
            ParticleInstance p = pool[active[i]];
            if (p == null || !p.alive) continue;
            if (!isVisible(p, tickDelta, camPos, frustum)) continue;

            Map<Identifier, java.util.List<ParticleInstance>> texMap = buckets.get(p.renderType);
            if (texMap == null) continue;

            Identifier tex = resolveTextureCached(p);
            texMap.computeIfAbsent(tex, k -> new ArrayList<>()).add(p);
        }

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f pose = matrices.peek().getPositionMatrix();

        for (RenderType type : RenderType.values()) {
            RenderLayerFactory factory = renderLayers.get(type);
            if (factory == null) continue;
            Map<Identifier, java.util.List<ParticleInstance>> texMap = buckets.get(type);
            if (texMap == null || texMap.isEmpty()) continue;

            for (Map.Entry<Identifier, java.util.List<ParticleInstance>> entry : texMap.entrySet()) {
                Identifier tex = entry.getKey();
                RenderLayer layer = factory.create(tex);
                VertexConsumer consumer = consumers.getBuffer(layer);
                for (ParticleInstance p : entry.getValue()) {
                    emitQuad(consumer, pose, p, tickDelta, camPos);
                }
            }
        }
        matrices.pop();
    }

    private void emitQuad(VertexConsumer consumer, Matrix4f pose, ParticleInstance p, float tickDelta, Vec3d camPos) {
        float half = p.size * 0.5f;

        float u0 = p.uvRegion != null ? p.uvRegion.u0() : 0f;
        float v0 = p.uvRegion != null ? p.uvRegion.v0() : 0f;
        float u1 = p.uvRegion != null ? p.uvRegion.u1() : 1f;
        float v1 = p.uvRegion != null ? p.uvRegion.v1() : 1f;

        int light = packLight(p.light);

        float r = p.colorSample.r();
        float g = p.colorSample.g();
        float b = p.colorSample.b();
        float a = p.alpha;

        float cx = lerp(p.prevX, p.x, tickDelta);
        float cy = lerp(p.prevY, p.y, tickDelta);
        float cz = lerp(p.prevZ, p.z, tickDelta);

        Basis base = basisForParticle(p, cx, cy, cz, camPos);

        int slices = Math.max(1, p.impostorSlices);

        float angleStep = (slices == 2) ? HALF_PI : (TWO_PI / slices);

        for (int i = 0; i < slices; i++) {
            float angle = i * angleStep;
            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);

            float rx = (base.rightX * cos + base.forwardX * sin) * half;
            float ry = (base.rightY * cos + base.forwardY * sin) * half;
            float rz = (base.rightZ * cos + base.forwardZ * sin) * half;

            float ux = base.upX * half;
            float uy = base.upY * half;
            float uz = base.upZ * half;

            float nx = (base.rightX * sin - base.forwardX * cos);
            float ny = (base.rightY * sin - base.forwardY * cos);
            float nz = (base.rightZ * sin - base.forwardZ * cos);

            addVertex(consumer, pose, cx - rx - ux, cy - ry - uy, cz - rz - uz, u0, v1, r, g, b, a, light, nx, ny, nz);
            addVertex(consumer, pose, cx - rx + ux, cy - ry + uy, cz - rz + uz, u0, v0, r, g, b, a, light, nx, ny, nz);
            addVertex(consumer, pose, cx + rx + ux, cy + ry + uy, cz + rz + uz, u1, v0, r, g, b, a, light, nx, ny, nz);
            addVertex(consumer, pose, cx + rx - ux, cy + ry - uy, cz + rz - uz, u1, v1, r, g, b, a, light, nx, ny, nz);
        }
    }

    private void addVertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z,
                           float u, float v, float r, float g, float b, float a, int light,
                           float nx, float ny, float nz) {
        consumer.vertex(pose, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(nx, ny, nz); // On passe la normale correcte transformée par la pose si nécessaire
    }

    private int packLight(com.moud.api.particle.LightSettings light) {
        int block = Math.max(0, Math.min(15, light.block()));
        int sky = Math.max(0, Math.min(15, light.sky()));
        return (sky << 20) | (block << 4);
    }

    private Identifier resolveTextureCached(ParticleInstance p) {
        if (p.resolvedTexture != null) {
            return p.resolvedTexture;
        }
        String key = p.texture;
        Identifier cached = textureCache.get(key);
        if (cached != null) {
            p.resolvedTexture = cached;
            return cached;
        }
        Identifier resolved = resolveTexture(key);
        textureCache.put(key, resolved);
        p.resolvedTexture = resolved;
        return resolved;
    }

    private Identifier resolveTexture(String id) {
        try {
            Identifier identifier = Identifier.of(id);
            var resource = net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(identifier);
            if (resource.isEmpty()) {
                Identifier alt = tryAlternatePath(identifier);
                if (alt != null) {
                    return alt;
                }
                com.moud.client.MoudClientMod.getLogger().warn("Particle texture missing: {}", id);
                return Identifier.of("minecraft", "textures/block/stone.png");
            }
            return identifier;
        } catch (Exception e) {
            com.moud.client.MoudClientMod.getLogger().warn("Invalid particle texture id {}: {}", id, e.getMessage());
            return Identifier.of("minecraft", "textures/block/stone.png");
        }
    }

    private Identifier tryAlternatePath(Identifier original) {
        String path = original.getPath();
        String basePath = path.endsWith(".png") ? path.substring(0, path.length() - 4) : path;

        // Try textures/particle/<path>.png
        Identifier particlePath = Identifier.of(original.getNamespace(), "textures/particle/" + basePath + ".png");
        var rm = net.minecraft.client.MinecraftClient.getInstance().getResourceManager();
        if (rm.getResource(particlePath).isPresent()) {
            return particlePath;
        }

        // Try textures/<path>.png
        Identifier texturesPath = Identifier.of(original.getNamespace(), "textures/" + basePath + ".png");
        if (rm.getResource(texturesPath).isPresent()) {
            return texturesPath;
        }
        return null;
    }


    private Basis basisForParticle(ParticleInstance p, float cx, float cy, float cz, Vec3d camPos) {
        Vec3d toCamera = new Vec3d(camPos.x - cx, camPos.y - cy, camPos.z - cz);
        if (toCamera.lengthSquared() < 1e-8) {
            toCamera = new Vec3d(0, 0, 1);
        }
        Vec3d forward = toCamera.normalize();
        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right = worldUp.crossProduct(forward);
        if (right.lengthSquared() < 1e-6) {
            right = new Vec3d(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3d up = forward.crossProduct(right).normalize();

        switch (p.billboarding) {
            case VELOCITY_ALIGNED -> {
                float vx = p.vx;
                float vy = p.vy;
                float vz = p.vz;
                float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (len > 1e-4f) {
                    Vec3d velForward = new Vec3d(vx / len, vy / len, vz / len);
                    Vec3d velRight = worldUp.crossProduct(velForward);
                    if (velRight.lengthSquared() < 1e-6) {
                        velRight = right;
                    } else {
                        velRight = velRight.normalize();
                    }
                    Vec3d velUp = velForward.crossProduct(velRight).normalize();
                    right = velRight;
                    up = velUp;
                    forward = velForward;
                }
            }
            case AXIS_LOCKED -> {
                Vec3d horiz = new Vec3d(camPos.x - cx, 0, camPos.z - cz);
                if (horiz.lengthSquared() < 1e-6) {
                    horiz = new Vec3d(0, 0, 1);
                }
                Vec3d horizF = horiz.normalize();
                Vec3d horizR = worldUp.crossProduct(horizF).normalize();
                Vec3d horizU = worldUp;
                right = horizR;
                up = horizU;
                forward = horizF;
            }
            case NONE -> {
                right = new Vec3d(1, 0, 0);
                up = new Vec3d(0, 1, 0);
                forward = new Vec3d(0, 0, 1);
            }
            case CAMERA_FACING -> { /* already computed */ }
        }

        if (p.rotation != 0f) {
            float sin = (float) Math.sin(p.rotation);
            float cos = (float) Math.cos(p.rotation);
            double newRx = right.x * cos + up.x * sin;
            double newRy = right.y * cos + up.y * sin;
            double newRz = right.z * cos + up.z * sin;
            double newUx = up.x * cos - right.x * sin;
            double newUy = up.y * cos - right.y * sin;
            double newUz = up.z * cos - right.z * sin;
            right = new Vec3d(newRx, newRy, newRz);
            up = new Vec3d(newUx, newUy, newUz);
        }

        return new Basis((float) right.x, (float) right.y, (float) right.z,
                (float) up.x, (float) up.y, (float) up.z,
                (float) forward.x, (float) forward.y, (float) forward.z);
    }

    private boolean isVisible(ParticleInstance p, float tickDelta, Vec3d camPos, net.minecraft.client.render.Frustum frustum) {
        if (frustum == null) {
            return true;
        }
        float lerpX = lerp(p.prevX, p.x, tickDelta);
        float lerpY = lerp(p.prevY, p.y, tickDelta);
        float lerpZ = lerp(p.prevZ, p.z, tickDelta);
        float half = p.size * 0.5f;
        double minX = lerpX - half;
        double minY = lerpY - half;
        double minZ = lerpZ - half;
        double maxX = lerpX + half;
        double maxY = lerpY + half;
        double maxZ = lerpZ + half;
        return frustum.isVisible(new net.minecraft.util.math.Box(minX, minY, minZ, maxX, maxY, maxZ));
    }

    private record Basis(float rightX, float rightY, float rightZ,
                         float upX, float upY, float upZ,
                         float forwardX, float forwardY, float forwardZ) {
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @FunctionalInterface
    private interface RenderLayerFactory {
        RenderLayer create(Identifier texture);
    }
}
