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
import java.util.Map;

public final class ParticleRenderer {
    private static final int MAX_PARTICLES_PER_FRAME = 8192;

    private final ParticleSystem system;
    private final Map<RenderType, RenderLayerFactory> renderLayers = new EnumMap<>(RenderType.class);
    private static boolean textureDumped = false;

    public ParticleRenderer(ParticleSystem system) {
        this.system = system;
        renderLayers.put(RenderType.TRANSLUCENT, RenderLayer::getEntityTranslucent);
        renderLayers.put(RenderType.ADDITIVE, RenderLayer::getEntityTranslucentEmissive);
        renderLayers.put(RenderType.CUTOUT, RenderLayer::getEntityCutout);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, float tickDelta) {
        int count = Math.min(system.getActiveCount(), MAX_PARTICLES_PER_FRAME);
        if (count == 0) {
            return;
        }

        ParticleInstance[] pool = system.getParticles();

        Vec3d camPos = camera.getPos();
        Basis basis = computeBasis(camera, camPos, pool, tickDelta);

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f pose = matrices.peek().getPositionMatrix();

        for (RenderType type : RenderType.values()) {
            RenderLayerFactory factory = renderLayers.get(type);
            if (factory == null) continue;

            int emitted = 0;
            for (ParticleInstance p : pool) {
                if (p == null || !p.alive || p.renderType != type) continue;
                Identifier tex = resolveTexture(p.texture);
                RenderLayer layer = factory.create(tex);
                VertexConsumer consumer = consumers.getBuffer(layer);
                emitQuad(consumer, pose, basis, p, tickDelta, camPos);
                emitted++;
                if (emitted >= count) break;
            }
        }
        matrices.pop();
    }

    private void emitQuad(VertexConsumer consumer, Matrix4f pose, Basis basis, ParticleInstance p, float tickDelta, Vec3d camPos) {
        Basis resolved = basisForParticle(basis, p, tickDelta, camPos);
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

        float lerpX = lerp(p.prevX, p.x, tickDelta);
        float lerpY = lerp(p.prevY, p.y, tickDelta);
        float lerpZ = lerp(p.prevZ, p.z, tickDelta);
        float cx = lerpX;
        float cy = lerpY;
        float cz = lerpZ;

        float rx = resolved.rightX * half;
        float ry = resolved.rightY * half;
        float rz = resolved.rightZ * half;
        float ux = resolved.upX * half;
        float uy = resolved.upY * half;
        float uz = resolved.upZ * half;

        addVertex(consumer, pose, cx - rx - ux, cy - ry - uy, cz - rz - uz, u0, v1, r, g, b, a, light);
        addVertex(consumer, pose, cx - rx + ux, cy - ry + uy, cz - rz + uz, u0, v0, r, g, b, a, light);
        addVertex(consumer, pose, cx + rx + ux, cy + ry + uy, cz + rz + uz, u1, v0, r, g, b, a, light);
        addVertex(consumer, pose, cx + rx - ux, cy + ry - uy, cz + rz - uz, u1, v1, r, g, b, a, light);
    }

    private void addVertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z,
                           float u, float v, float r, float g, float b, float a, int light) {
        consumer.vertex(pose, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0f, 1f, 0f);
    }

    private int packLight(com.moud.api.particle.LightSettings light) {
        int block = Math.max(0, Math.min(15, light.block()));
        int sky = Math.max(0, Math.min(15, light.sky()));
        return (sky << 20) | (block << 4);
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
            dumpAvailableTexturesOnce();
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
        var resParticle = rm.getResource(particlePath);
        if (resParticle.isPresent()) {
            com.moud.client.MoudClientMod.getLogger().info("Resolved particle texture via particle path: {} -> {}", original, particlePath);
            return particlePath;
        }

        // Try textures/<path>.png
        Identifier texturesPath = Identifier.of(original.getNamespace(), "textures/" + basePath + ".png");
        var resTextures = rm.getResource(texturesPath);
        if (resTextures.isPresent()) {
            com.moud.client.MoudClientMod.getLogger().info("Resolved particle texture via textures path: {} -> {}", original, texturesPath);
            return texturesPath;
        }
        return null;
    }

    private void dumpAvailableTexturesOnce() {
        if (textureDumped) {
            return;
        }
        textureDumped = true;
        try {
            var rm = net.minecraft.client.MinecraftClient.getInstance().getResourceManager();
            var resources = rm.findResources("textures/particle", id -> id.getPath().endsWith(".png"));
            com.moud.client.MoudClientMod.getLogger().warn("Available particle textures:");
            for (var entry : resources.keySet()) {
                com.moud.client.MoudClientMod.getLogger().warn(" - {}", entry.toString());
            }
        } catch (Exception e) {
            com.moud.client.MoudClientMod.getLogger().warn("Failed to list particle textures: {}", e.getMessage());
        }
    }

    private Basis computeBasis(Camera camera, Vec3d camPos, ParticleInstance[] pool, float tickDelta) {
        float yaw = (float) Math.toRadians(camera.getYaw());
        float pitch = (float) Math.toRadians(camera.getPitch());
        float cosYaw = (float) Math.cos(yaw);
        float sinYaw = (float) Math.sin(yaw);
        float cosPitch = (float) Math.cos(pitch);
        float sinPitch = (float) Math.sin(pitch);

        Vec3d forward = new Vec3d(-sinYaw * cosPitch, sinPitch, -cosYaw * cosPitch).normalize();
        Vec3d upVec = new Vec3d(0, 1, 0);
        Vec3d right = upVec.crossProduct(forward).normalize();
        Vec3d up = forward.crossProduct(right).normalize();

        return new Basis((float) right.x, (float) right.y, (float) right.z,
                (float) up.x, (float) up.y, (float) up.z,
                (float) forward.x, (float) forward.y, (float) forward.z);
    }

    private Basis basisForParticle(Basis cameraBasis, ParticleInstance p, float tickDelta, Vec3d camPos) {
        float rx = cameraBasis.rightX;
        float ry = cameraBasis.rightY;
        float rz = cameraBasis.rightZ;
        float ux = cameraBasis.upX;
        float uy = cameraBasis.upY;
        float uz = cameraBasis.upZ;
        float fx = cameraBasis.forwardX;
        float fy = cameraBasis.forwardY;
        float fz = cameraBasis.forwardZ;

        switch (p.billboarding) {
            case VELOCITY_ALIGNED -> {
            float vx = p.vx;
            float vy = p.vy;
            float vz = p.vz;
            float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (len > 1e-4f) {
                fx = vx / len;
                fy = vy / len;
                fz = vz / len;
                Vec3d upVec = new Vec3d(0, 1, 0);
                Vec3d forward = new Vec3d(fx, fy, fz);
                Vec3d right = upVec.crossProduct(forward).normalize();
                Vec3d up = forward.crossProduct(right).normalize();
                rx = (float) right.x;
                ry = (float) right.y;
                rz = (float) right.z;
                ux = (float) up.x;
                uy = (float) up.y;
                uz = (float) up.z;
            }
        }
            case AXIS_LOCKED -> {
            Vec3d toCamera = new Vec3d(camPos.x - p.x, 0, camPos.z - p.z);
            if (toCamera.lengthSquared() < 1e-4) {
                toCamera = new Vec3d(0, 0, 1);
            }
            Vec3d forward = toCamera.normalize();
            Vec3d up = new Vec3d(0, 1, 0);
            Vec3d right = up.crossProduct(forward).normalize();
            rx = (float) right.x;
            ry = (float) right.y;
            rz = (float) right.z;
            ux = (float) up.x;
            uy = (float) up.y;
            uz = (float) up.z;
            fx = (float) forward.x;
            fy = (float) forward.y;
            fz = (float) forward.z;
        }
        case CAMERA_FACING -> {
        }
        }

        if (p.rotation != 0f) {
            float sin = (float) Math.sin(p.rotation);
            float cos = (float) Math.cos(p.rotation);
            float newRx = rx * cos + ux * sin;
            float newRy = ry * cos + uy * sin;
            float newRz = rz * cos + uz * sin;
            float newUx = ux * cos - rx * sin;
            float newUy = uy * cos - ry * sin;
            float newUz = uz * cos - rz * sin;
            rx = newRx;
            ry = newRy;
            rz = newRz;
            ux = newUx;
            uy = newUy;
            uz = newUz;
        }

        return new Basis(rx, ry, rz, ux, uy, uz, fx, fy, fz);
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
