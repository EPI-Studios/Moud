package com.moud.client.fabric.render.scene.subrender.particle;

import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.scene.util.NodePropertyUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ParticleDrawer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private final MatrixStack identityStack = new MatrixStack();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private final Vector3f axisX = new Vector3f();
    private final Vector3f axisY = new Vector3f();
    private final Vector3f axisN = new Vector3f();
    private final Vector3f tmpA = new Vector3f();
    private final Vector3f tmpB = new Vector3f();
    private final BlockPos.Mutable lightPos = new BlockPos.Mutable();
    private long lightCacheBlock = Long.MIN_VALUE;
    private int lightCacheValue;

    public void draw(EmitterState state, EmitterConfig c, VertexConsumerProvider.Immediate consumers,
                     Vec3d camPos, Camera camera, MinecraftClient client) {
        if (!c.visible || state.particles.isEmpty()) return;

        Identifier tex = MoudTextures.resolve(c.texture);
        RenderLayer layer = (c.additive || c.distortion) ? RenderLayer.getEntityNoOutline(tex) : RenderLayer.getEntityTranslucent(tex);
        VertexConsumer vc = consumers.getBuffer(layer);
        float lodScale = state.lodSizeScale > 0f ? state.lodSizeScale : 1f;

        Quaternionf camRot = camera.getRotation();
        right.set(1f, 0f, 0f).rotate(camRot);
        up.set(0f, 1f, 0f).rotate(camRot);
        normal.set(0f, 0f, 1f).rotate(camRot);

        int totalFrames = Math.max(1, c.frameCount);
        float invH = 1f / c.hframes;
        float invV = 1f / c.vframes;
        lightCacheBlock = Long.MIN_VALUE;

        MatrixStack.Entry entry = identityStack.peek();
        int overlay = OverlayTexture.DEFAULT_UV;
        boolean velocityMode = "velocity".equals(c.billboardMode);
        int mode = switch (c.billboardMode) {
            case "velocity" -> 1;
            case "y_axis" -> 2;
            case "world" -> 3;
            default -> 0;
        };

        int count = state.particles.size();
        for (int i = 0; i < count; i++) {
            Particle p = state.particles.get(i);
            float t = p.lifetime > 0f ? Math.min(1f, p.age / p.lifetime) : 1f;
            float sizeBase = lerp(p.sizeStart, p.sizeEnd, t);
            float size = sizeBase * c.sizeCurve.sample(t) * lodScale;
            if (size <= 0.0001f) continue;

            float r = lerp(p.r0, p.r1, t);
            float g = lerp(p.g0, p.g1, t);
            float b = lerp(p.b0, p.b1, t);
            float a = c.useAlphaCurve ? NodePropertyUtils.clamp01(c.alphaCurve.sample(t)) : lerp(p.a0, p.a1, t);
            if (c.softParticles && c.softFadeDistance > 0f) {
                a *= softFadeFactor(client, p.x, p.y, p.z, c.softFadeDistance);
            }
            if (c.distortion) {
                a *= NodePropertyUtils.clamp01(c.distortionStrength);
            }
            int ri = byteOf(r), gi = byteOf(g), bi = byteOf(b), ai = byteOf(a);
            if (ai <= 0) continue;

            int light = c.unlit ? FULL_BRIGHT : sampleLightCached(client, p);

            float halfW = size * 0.5f;
            float halfH = size * 0.5f;

            switch (mode) {
                case 1 -> {
                    float speed = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
                    if (speed > 1.0e-4f) {
                        float inv = 1f / speed;
                        tmpA.set(p.vx * inv, p.vy * inv, p.vz * inv);
                        tmpB.set((float) (camPos.x - p.x), (float) (camPos.y - p.y), (float) (camPos.z - p.z));
                        float toCamLen2 = tmpB.lengthSquared();
                        if (toCamLen2 > 1.0e-6f) tmpB.mul(1f / (float) Math.sqrt(toCamLen2));
                        axisX.set(tmpA).cross(tmpB);
                        if (axisX.lengthSquared() < 1.0e-6f) axisX.set(1f, 0f, 0f);
                        else axisX.normalize();
                        axisN.set(tmpA).cross(axisX);
                        float stretchHalf = halfH * Math.max(0.01f, c.stretchScale);
                        axisY.set(tmpA).mul(stretchHalf);
                        axisX.mul(halfW);
                    } else {
                        axisX.set(right).mul(halfW);
                        axisY.set(up).mul(halfH);
                        axisN.set(normal);
                    }
                }
                case 2 -> {
                    tmpA.set((float) (camPos.x - p.x), 0f, (float) (camPos.z - p.z));
                    if (tmpA.lengthSquared() < 1.0e-6f) tmpA.set(0f, 0f, 1f);
                    tmpA.normalize();
                    axisX.set(0f, 1f, 0f).cross(tmpA).normalize().mul(halfW);
                    axisY.set(0f, halfH, 0f);
                    axisN.set(tmpA);
                }
                case 3 -> {
                    axisX.set(halfW, 0f, 0f);
                    axisY.set(0f, halfH, 0f);
                    axisN.set(0f, 0f, 1f);
                }
                default -> {
                    axisX.set(right).mul(halfW);
                    axisY.set(up).mul(halfH);
                    axisN.set(normal);
                }
            }

            if (!velocityMode && Math.abs(p.rotation) > 1.0e-5f) {
                float cs = (float) Math.cos(p.rotation);
                float sn = (float) Math.sin(p.rotation);
                float ax = axisX.x * cs + axisY.x * sn;
                float ay = axisX.y * cs + axisY.y * sn;
                float az = axisX.z * cs + axisY.z * sn;
                float bx = axisY.x * cs - axisX.x * sn;
                float by = axisY.y * cs - axisX.y * sn;
                float bz = axisY.z * cs - axisX.z * sn;
                axisX.set(ax, ay, az);
                axisY.set(bx, by, bz);
            }

            float cx = (float) (p.x - camPos.x);
            float cy = (float) (p.y - camPos.y);
            float cz = (float) (p.z - camPos.z);

            float u0, u1, v0, v1;
            if (totalFrames > 1) {
                int frame = resolveFrame(c, p, t, totalFrames);
                int col = frame % c.hframes;
                int row = frame / c.hframes;
                u0 = col * invH; u1 = u0 + invH;
                v0 = row * invV; v1 = v0 + invV;
            } else {
                u0 = 0f; u1 = 1f; v0 = 0f; v1 = 1f;
            }

            emitQuad(vc, entry, cx, cy, cz, axisX, axisY, axisN, u0, u1, v0, v1, light, overlay, ri, gi, bi, ai);
        }
    }

    private float softFadeFactor(MinecraftClient client, double x, double y, double z, float fadeDist) {
        if (client == null || client.world == null) return 1f;
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        try {
            lightPos.set(bx, by, bz);
            boolean inside = !client.world.getBlockState(lightPos).getCollisionShape(client.world, lightPos).isEmpty();
            if (inside) return 0f;
            int bxd = (int) Math.floor(x - fadeDist);
            int bxu = (int) Math.floor(x + fadeDist);
            int byd = (int) Math.floor(y - fadeDist);
            int byu = (int) Math.floor(y + fadeDist);
            int bzd = (int) Math.floor(z - fadeDist);
            int bzu = (int) Math.floor(z + fadeDist);
            if (bxd == bx && bxu == bx && byd == by && byu == by && bzd == bz && bzu == bz) return 1f;
            double minDist = Double.MAX_VALUE;
            for (int cx = bxd; cx <= bxu; cx++) {
                for (int cy = byd; cy <= byu; cy++) {
                    for (int cz = bzd; cz <= bzu; cz++) {
                        if (cx == bx && cy == by && cz == bz) continue;
                        lightPos.set(cx, cy, cz);
                        if (client.world.getBlockState(lightPos).getCollisionShape(client.world, lightPos).isEmpty()) continue;
                        double dcx = Math.max(cx, Math.min(x, cx + 1));
                        double dcy = Math.max(cy, Math.min(y, cy + 1));
                        double dcz = Math.max(cz, Math.min(z, cz + 1));
                        double dx = x - dcx, dy = y - dcy, dz = z - dcz;
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 < minDist) minDist = d2;
                    }
                }
            }
            if (minDist == Double.MAX_VALUE) return 1f;
            float d = (float) Math.sqrt(minDist);
            return NodePropertyUtils.clamp01(d / fadeDist);
        } catch (Exception ignored) {
            return 1f;
        }
    }

    private int sampleLightCached(MinecraftClient client, Particle p) {
        if (client == null || client.world == null) return FULL_BRIGHT;
        int bx = (int) Math.floor(p.x);
        int by = (int) Math.floor(p.y);
        int bz = (int) Math.floor(p.z);
        long key = (((long) bx & 0x1FFFFFFL) << 38) | (((long) bz & 0x1FFFFFFL) << 13) | ((long) by & 0x1FFFL);
        if (key == lightCacheBlock) return lightCacheValue;
        lightPos.set(bx, by, bz);
        int l = WorldRenderer.getLightmapCoordinates(client.world, lightPos);
        if (l == 0) l = FULL_BRIGHT;
        lightCacheBlock = key;
        lightCacheValue = l;
        return l;
    }

    private static int resolveFrame(EmitterConfig c, Particle p, float t, int totalFrames) {
        if (c.frameFps > 0f) {
            int step = (int) (p.age * c.frameFps);
            return (p.frameOffset + step) % totalFrames;
        }
        int idx = Math.min(totalFrames - 1, (int) (t * totalFrames));
        return (p.frameOffset + idx) % totalFrames;
    }

    private static void emitQuad(VertexConsumer vc, MatrixStack.Entry entry,
                                 float cx, float cy, float cz,
                                 Vector3f ax, Vector3f ay, Vector3f n,
                                 float u0, float u1, float v0, float v1,
                                 int light, int overlay, int r, int g, int b, int a) {
        float nx = n.x, ny = n.y, nz = n.z;
        vertex(vc, entry, cx - ax.x - ay.x, cy - ax.y - ay.y, cz - ax.z - ay.z, u0, v1, light, overlay, r, g, b, a, nx, ny, nz);
        vertex(vc, entry, cx + ax.x - ay.x, cy + ax.y - ay.y, cz + ax.z - ay.z, u1, v1, light, overlay, r, g, b, a, nx, ny, nz);
        vertex(vc, entry, cx + ax.x + ay.x, cy + ax.y + ay.y, cz + ax.z + ay.z, u1, v0, light, overlay, r, g, b, a, nx, ny, nz);
        vertex(vc, entry, cx - ax.x + ay.x, cy - ax.y + ay.y, cz - ax.z + ay.z, u0, v0, light, overlay, r, g, b, a, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float z,
                               float u, float v, int light, int overlay,
                               int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.vertex(entry, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(entry, nx, ny, nz);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int byteOf(float f) {
        int i = Math.round(NodePropertyUtils.clamp01(f) * 255f);
        if (i < 0) return 0;
        if (i > 255) return 255;
        return i;
    }
}
