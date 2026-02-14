package com.moud.client.fabric.platform;

import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.mixin.accessor.CameraAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class MinecraftFreeflyCamera {
    private boolean enabled;
    private boolean capturing;

    private Vec3d pos = new Vec3d(0.0, 70.0, 0.0);
    private double yaw = 180.0;
    private double pitch = -25.0;
    private double targetYaw = yaw;
    private double targetPitch = pitch;

    private double lastMouseX;
    private double lastMouseY;
    private boolean firstMouseMove = true;

    private long lastUpdateNs;

    private float lookSensitivity = 0.15f;
    private double smoothing = 0.35; // 0..1
    private double speed = 10.0; // blocks/sec
    private double fastMultiplier = 4.0;
    private double slowMultiplier = 0.25;

    public MinecraftFreeflyCamera() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            bootstrapFromPlayer();
            lastUpdateNs = 0L;
            firstMouseMove = true;
            capturing = false;
        } else {
            capturing = false;
            lastUpdateNs = 0L;
            firstMouseMove = true;
        }
    }

    private void bootstrapFromPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            pos = client.player.getPos().add(0.0, client.player.getEyeHeight(client.player.getPose()), 0.0);
            targetYaw = client.player.getYaw();
            targetPitch = client.player.getPitch();
            yaw = targetYaw;
            pitch = targetPitch;
        }
    }

    public boolean consumeMouseButton(int button, int action, double mouseX, double mouseY) {
        if (!enabled) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null) {
            return false;
        }

        if (action == GLFW.GLFW_PRESS) {
            EditorContext ctx = EditorOverlayBus.get();
            if (ctx == null || !ctx.isMouseOverViewport(mouseX, mouseY)) {
                return true;
            }
            capturing = true;
            firstMouseMove = true;
            if (client.mouse != null && !client.mouse.isCursorLocked()) {
                client.mouse.lockCursor();
            }
            return true;
        }

        if (action == GLFW.GLFW_RELEASE) {
            capturing = false;
            firstMouseMove = true;
            if (client.mouse != null && client.mouse.isCursorLocked()) {
                client.mouse.unlockCursor();
            }
            return true;
        }

        return true;
    }

    public void onCursorModeChanged() {
        firstMouseMove = true;
    }

    public boolean consumeMouseMove(double mouseX, double mouseY) {
        if (!enabled) {
            return false;
        }

        if (firstMouseMove) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            firstMouseMove = false;
            return capturing;
        }

        double dx = mouseX - lastMouseX;
        double dy = mouseY - lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (!capturing) {
            return false;
        }

        targetYaw -= dx * lookSensitivity;
        targetPitch -= dy * lookSensitivity;
        targetPitch = MathHelper.clamp(targetPitch, -89.0, 89.0);
        return true;
    }

    public void updateRenderState() {
        if (!enabled) {
            lastUpdateNs = 0L;
            return;
        }

        long now = System.nanoTime();
        if (lastUpdateNs == 0L) {
            lastUpdateNs = now;
            return;
        }

        double dt = (now - lastUpdateNs) / 1_000_000_000.0;
        lastUpdateNs = now;
        dt = MathHelper.clamp(dt, 0.0, 0.1);
        if (dt <= 0.0) {
            return;
        }

        double smoothFactor = 1.0 - Math.pow(1.0 - MathHelper.clamp(smoothing, 0.01, 0.999), dt * 60.0);
        yaw = wrapAngle(yaw, targetYaw, smoothFactor);
        pitch = pitch + (MathHelper.clamp(targetPitch, -89.0, 89.0) - pitch) * smoothFactor;

        if (capturing) {
            applyMovement(dt);
        }
    }

    public boolean applyToCamera(Camera camera) {
        if (!enabled) {
            return false;
        }
        if (!(camera instanceof CameraAccessor accessor)) {
            return false;
        }
        accessor.moud$setThirdPerson(false);
        accessor.moud$setCameraPosition(pos.x, pos.y, pos.z);
        accessor.moud$setRotation((float) yaw, (float) pitch);
        return true;
    }

    private void applyMovement(double dt) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        long window = client.getWindow().getHandle();

        double currentSpeed = speed;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
            currentSpeed *= fastMultiplier;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) {
            currentSpeed *= slowMultiplier;
        }

        double forward = 0.0;
        double strafe = 0.0;
        double up = 0.0;

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) forward += 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) forward -= 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) strafe += 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) strafe -= 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) up += 1.0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS) up -= 1.0;

        Vec3d dir = forwardDir();
        Vec3d right = new Vec3d(dir.z, 0.0, -dir.x);
        Vec3d move = dir.multiply(forward).add(right.multiply(strafe)).add(0.0, up, 0.0);
        double len = move.length();
        if (len < 1e-6) {
            return;
        }
        move = move.multiply(1.0 / len);
        pos = pos.add(move.multiply(currentSpeed * dt));
    }

    private Vec3d forwardDir() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        double x = -Math.sin(yawRad) * cosPitch;
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * cosPitch;
        return new Vec3d(x, y, z);
    }

    private static double wrapAngle(double current, double target, double t) {
        double delta = MathHelper.wrapDegrees(target - current);
        return current + delta * t;
    }
}
