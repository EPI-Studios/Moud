package com.moud.client.display;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
public final class DisplayRenderer {
    private static final float EPSILON = 1.0e-4f;
    public void render(DisplaySurface surface, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        Identifier texture = surface.resolveTexture(tickDelta);
        if (texture == null || consumers == null) {
            return;
        }
        boolean renderOnTop = surface.isRenderThroughBlocks();
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        int finalLight = renderOnTop ? 0xF000F0 : light;
        RenderLayer layer = renderOnTop
                ? DisplayRenderLayers.getOnTopLayer(texture)
                : DisplayRenderLayers.getLayer(texture);
        VertexConsumer vertexConsumer = consumers.getBuffer(layer);
        matrices.push();
        Quaternion rotation = surface.getInterpolatedRotation(tickDelta);
        Quaternionf rotationQuat = new Quaternionf(rotation.x, rotation.y, rotation.z, rotation.w);
        MoudPackets.DisplayBillboardMode billboardMode = surface.getBillboardMode();
        if (billboardMode != null && billboardMode != MoudPackets.DisplayBillboardMode.NONE) {
            Quaternionf camRot = camera.getRotation();
            switch (billboardMode) {
                case CAMERA_FACING -> {
                    rotationQuat.set(camRot);
                    rotationQuat.rotateY((float) Math.PI);
                }
                case VERTICAL -> {
                    float yaw = -camera.getYaw();
                    rotationQuat.rotationY((float) Math.toRadians(yaw));
                    rotationQuat.rotateY((float) Math.PI);
                }
                default -> {}
            }
        }
        matrices.multiply(rotationQuat);
        Vector3 scale = surface.getInterpolatedScale(tickDelta);
        float sx = Math.max(scale.x, EPSILON);
        float sy = Math.max(scale.y, EPSILON);
        float sz = Math.max(scale.z, EPSILON);
        matrices.scale(sx, sy, sz);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        addQuad(surface, vertexConsumer, positionMatrix, entry, finalLight);
        matrices.pop();
    }
    private void addQuad(DisplaySurface surface, VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry, int light) {
        float halfWidth = 0.5f;
        float halfHeight = 0.5f;
        float z = 0.0f;
        float x0 = -halfWidth, y0 = -halfHeight;
        float x1 = halfWidth,  y1 = -halfHeight;
        float x2 = halfWidth,  y2 = halfHeight;
        float x3 = -halfWidth, y3 = halfHeight;
        writeVertex(surface, consumer, matrix, entry, x0, y0, z, 0.0f, 1.0f, light);
        writeVertex(surface, consumer, matrix, entry, x1, y1, z, 1.0f, 1.0f, light);
        writeVertex(surface, consumer, matrix, entry, x2, y2, z, 1.0f, 0.0f, light);
        writeVertex(surface, consumer, matrix, entry, x3, y3, z, 0.0f, 0.0f, light);
    }
    private void writeVertex(DisplaySurface surface, VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry,
                             float x, float y, float z, float u, float v, int light) {
        consumer.vertex(matrix, x, y, z)
                .color(1.0f, 1.0f, 1.0f, surface.getOpacity())
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0f, 0.0f, 1.0f);
    }
}