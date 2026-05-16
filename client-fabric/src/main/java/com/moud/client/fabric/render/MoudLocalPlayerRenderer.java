package com.moud.client.fabric.render;

import com.moud.client.fabric.runtime.MoudPlayer;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class MoudLocalPlayerRenderer {
    private static final MoudLocalPlayerRenderer INSTANCE = new MoudLocalPlayerRenderer();

    private float limbAngle;
    private float limbDistance;
    private long lastFrameNanos;

    private MoudLocalPlayerRenderer() {}

    public static MoudLocalPlayerRenderer get() { return INSTANCE; }

    public void reset() {
        limbAngle = 0f;
        limbDistance = 0f;
        lastFrameNanos = 0L;
    }

    public void render(Camera camera, MatrixStack matrices, VertexConsumerProvider provider) {
        if (camera == null || matrices == null || provider == null) return;
        PlayRuntimeClient rt = PlayRuntimeBus.get();
        if (rt == null || !rt.isActive() || !rt.isUsingSceneCamera()) {
            lastFrameNanos = 0L;
            return;
        }
        MoudPlayer player = rt.player();
        if (player == null || !player.isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getEntityRenderDispatcher() == null) return;

        EntityRenderer<? super AbstractClientPlayerEntity> raw =
                mc.getEntityRenderDispatcher().getRenderer(mc.player);
        if (!(raw instanceof PlayerEntityRenderer playerRenderer)) return;
        PlayerEntityModel<AbstractClientPlayerEntity> model = playerRenderer.getModel();
        if (model == null) return;

        long now = System.nanoTime();
        double dt;
        if (lastFrameNanos == 0L) dt = 1.0 / 60.0;
        else {
            dt = (now - lastFrameNanos) / 1_000_000_000.0;
            if (dt > 0.1) dt = 0.1;
            if (dt <= 0.0) dt = 1.0 / 120.0;
        }
        lastFrameNanos = now;

        double speedH = Math.hypot(player.vx(), player.vz());
        float targetDist = (float) Math.min(1.0, speedH * 0.5);
        limbDistance = limbDistance + (targetDist - limbDistance) * (float) Math.min(1.0, dt * 8.0);
        limbAngle += (float) (speedH * dt * 2.0);

        float headYaw = wrapDeg(player.yaw() - player.bodyYaw());
        model.setAngles(mc.player, limbAngle, limbDistance, limbAngle, headYaw, player.pitch());
        model.handSwingProgress = 0f;
        model.riding = false;
        model.child = false;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(player.x() - camPos.x, player.y() - camPos.y, player.z() - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - player.bodyYaw()));
        if (Math.abs(player.rotationZ()) > 1e-4f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(player.rotationZ()));
        }
        matrices.scale(-1f, -1f, 1f);
        matrices.translate(0f, -1.501f, 0f);

        Identifier skin = playerRenderer.getTexture(mc.player);
        VertexConsumer consumer = provider.getBuffer(model.getLayer(skin));
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);

        matrices.pop();
    }

    private static float wrapDeg(float v) {
        float r = v % 360f;
        if (r > 180f) r -= 360f;
        else if (r < -180f) r += 360f;
        return r;
    }
}
