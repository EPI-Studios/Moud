package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.mixin.accessor.GameRendererAccessor;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MouseApi {
    private boolean prevLeft;
    private boolean prevRight;
    private boolean prevMiddle;

    public MouseApi() {
    }

    public boolean isLeftDown()   { return readButton(GLFW.GLFW_MOUSE_BUTTON_LEFT); }
    public boolean isRightDown()  { return readButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
    public boolean isMiddleDown() { return readButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE); }

    public boolean isLeftPressed() {
        boolean now = isLeftDown();
        boolean edge = now && !prevLeft;
        prevLeft = now;
        return edge;
    }

    public boolean isRightPressed() {
        boolean now = isRightDown();
        boolean edge = now && !prevRight;
        prevRight = now;
        return edge;
    }

    public boolean isMiddlePressed() {
        boolean now = isMiddleDown();
        boolean edge = now && !prevMiddle;
        prevMiddle = now;
        return edge;
    }

    private static boolean readButton(int glfwButton) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !client.isWindowFocused()) return false;
        Window window = client.getWindow();
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null && runtime.isCursorModeEnabled()) return false;
        return GLFW.glfwGetMouseButton(window.getHandle(), glfwButton) == GLFW.GLFW_PRESS;
    }

    public void unlock() {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null) {
            runtime.setCursorModeEnabled(true);
        }
    }

    public void lock() {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null) {
            runtime.setCursorModeEnabled(false);
        }
    }

    public void setVisible(boolean visible) {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null) {
            runtime.setOsCursorVisible(visible);
        }
    }

    public boolean isUnlocked() {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        return runtime != null && runtime.isCursorModeEnabled();
    }

    public boolean isVisible() {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        return runtime == null || runtime.isOsCursorVisible();
    }

    public float[] cursorPos() {
        PlayRuntimeClient runtime = PlayRuntimeBus.get();
        if (runtime != null) {
            return new float[]{runtime.cursorX(), runtime.cursorY()};
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return new float[]{0f, 0f};
        }
        Window window = client.getWindow();
        return new float[]{window.getWidth() * 0.5f, window.getHeight() * 0.5f};
    }

    public float[] viewportSize() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return new float[]{0f, 0f};
        }
        Window window = client.getWindow();
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetWindowSize(window.getHandle(), w, h);
        if (w[0] > 0 && h[0] > 0) {
            return new float[]{w[0], h[0]};
        }
        return new float[]{window.getWidth(), window.getHeight()};
    }

    public double[] rayFromCursor(double maxDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client != null && client.gameRenderer != null ? client.gameRenderer.getCamera() : null;
        Window window = client != null ? client.getWindow() : null;
        if (client == null || camera == null || window == null || !client.isWindowFocused()) {
            return new double[]{0.0, 0.0, 0.0, 0.0, 0.0, -1.0};
        }

        float[] cursor = cursorPos();
        double width = Math.max(1.0, window.getWidth());
        double height = Math.max(1.0, window.getHeight());

        double ndcX = (cursor[0] / width) * 2.0 - 1.0;
        double ndcY = 1.0 - (cursor[1] / height) * 2.0;
        double aspect = width / height;
        double fovDeg = ((GameRendererAccessor) client.gameRenderer).moud$getFov(camera, 1.0f, true);
        double tanHalfFov = Math.tan(Math.toRadians(fovDeg) * 0.5);

        Vector3f dirCamera = new Vector3f(
                (float) (ndcX * aspect * tanHalfFov),
                (float) (ndcY * tanHalfFov),
                -1.0f
        ).normalize();

        Quaternionf rotation = new Quaternionf(camera.getRotation());
        rotation.transform(dirCamera);

        Vec3d origin = camera.getPos();
        return new double[]{
                origin.x, origin.y, origin.z,
                dirCamera.x, dirCamera.y, dirCamera.z
        };
    }

    public double[] raycastFromCursor(double maxDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client != null && client.gameRenderer != null ? client.gameRenderer.getCamera() : null;
        if (client == null || client.world == null || camera == null) {
            return miss();
        }

        double dist = Math.max(0.01, maxDistance);
        double[] ray = rayFromCursor(dist);
        Vec3d origin = new Vec3d(ray[0], ray[1], ray[2]);
        Vec3d dir = new Vec3d(ray[3], ray[4], ray[5]).normalize();
        Vec3d end = origin.add(dir.multiply(dist));

        BlockHitResult hit = client.world.raycast(new RaycastContext(
                origin,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.getCameraEntity() != null ? client.getCameraEntity() : client.player
        ));

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return miss();
        }

        Vec3d pos = hit.getPos();
        Direction side = hit.getSide();
        return new double[]{
                1.0,
                pos.x, pos.y, pos.z,
                side.getOffsetX(), side.getOffsetY(), side.getOffsetZ(),
                ray[3], ray[4], ray[5]
        };
    }

    public double[] cursorHit(double maxDistance) {
        return raycastFromCursor(maxDistance);
    }

    public double[] cursorPoint(double maxDistance) {
        double dist = Math.max(0.01, maxDistance);
        double[] hit = raycastFromCursor(dist);
        if (hit[0] > 0.5) {
            return new double[]{hit[1], hit[2], hit[3]};
        }
        double[] ray = rayFromCursor(dist);
        return new double[]{
                ray[0] + ray[3] * dist,
                ray[1] + ray[4] * dist,
                ray[2] + ray[5] * dist
        };
    }

    public String cursorEntity(double maxDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity cameraEntity = client != null ? client.getCameraEntity() : null;
        if (client == null || client.world == null || cameraEntity == null || !client.isWindowFocused()) {
            return "";
        }

        double dist = Math.max(0.01, maxDistance);
        double[] ray = rayFromCursor(dist);
        Vec3d origin = new Vec3d(ray[0], ray[1], ray[2]);
        Vec3d dir = new Vec3d(ray[3], ray[4], ray[5]).normalize();
        Vec3d end = origin.add(dir.multiply(dist));

        BlockHitResult blockHit = client.world.raycast(new RaycastContext(
                origin,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                cameraEntity
        ));

        double entityDistance = dist;
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            entityDistance = origin.squaredDistanceTo(blockHit.getPos());
        }

        Box searchBox = cameraEntity.getBoundingBox()
                .stretch(dir.multiply(dist))
                .expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                cameraEntity,
                origin,
                end,
                searchBox,
                target -> target != null && target.canHit() && !target.isSpectator(),
                entityDistance
        );
        if (entityHit == null) {
            return "";
        }
        return entityHit.getEntity().getUuidAsString();
    }

    private static double[] miss() {
        return new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0};
    }

    public boolean isFrontView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return false;
        }
        return client.options.getPerspective() == Perspective.THIRD_PERSON_FRONT;
    }
}
