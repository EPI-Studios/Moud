package com.moud.client.editor.camera;

import com.moud.client.editor.config.EditorConfig;
import com.moud.client.editor.scene.SceneObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public final class EditorCameraController {
    private static final EditorCameraController INSTANCE = new EditorCameraController();

    private final MinecraftClient client = MinecraftClient.getInstance();
    private boolean active;

    private Vec3d cameraPos = new Vec3d(0, 70, 0);

    private double yaw = 180.0;
    private double pitch = -25.0;
    private double targetYaw = yaw;
    private double targetPitch = pitch;

    private boolean cursorLocked = false;
    private boolean rightMouseDown = false;
    private boolean middleMouseDown = false;
    private EditorConfig.Camera cachedConfig;
    private long lastUpdateTimeNs = 0L;

    private EditorCameraController() {}

    public static EditorCameraController getInstance() {
        return INSTANCE;
    }

    public void enable(@Nullable SceneObject initialSelection) {
        if (active) {
            return;
        }
        this.cachedConfig = EditorConfig.getInstance().camera();
        if (client.player != null) {
            Vec3d playerPos = client.player.getPos();

            cameraPos = playerPos.add(0, 1.6, 0);

            targetYaw = client.player.getYaw();
            targetPitch = client.player.getPitch();
            yaw = targetYaw;
            pitch = targetPitch;
        } else {
            cameraPos = new Vec3d(0, 70, 0);
        }
        cursorLocked = false;
        unlockCursor();
        active = true;
        lastUpdateTimeNs = 0L;
    }

    public void disable() {
        if (!active) {
            return;
        }
        active = false;
        unlockCursor();
        lastUpdateTimeNs = 0L;
    }

    public boolean isActive() {
        return active;
    }

    public void tick() {
        if (!active) {
            lastUpdateTimeNs = 0L;
            return;
        }
        cachedConfig = EditorConfig.getInstance().camera();
    }

    public void updateRenderState() {
        if (!active) {
            lastUpdateTimeNs = 0L;
            return;
        }

        long now = System.nanoTime();
        if (lastUpdateTimeNs == 0L) {
            lastUpdateTimeNs = now;
            return;
        }

        double deltaSeconds = (now - lastUpdateTimeNs) / 1_000_000_000.0;
        lastUpdateTimeNs = now;
        if (deltaSeconds <= 0.0) {
            return;
        }

        deltaSeconds = MathHelper.clamp(deltaSeconds, 0.0, 0.25);
        double deltaTicks = deltaSeconds * 20.0;

        EditorConfig.Camera config = cachedConfig != null ? cachedConfig : EditorConfig.getInstance().camera();
        double smoothing = MathHelper.clamp(config.orbitSmoothing, 0.01, 0.999);
        double smoothFactor = 1.0 - Math.pow(1.0 - smoothing, deltaTicks);

        double clampedTargetPitch = MathHelper.clamp(targetPitch, -89.0, 89.0);
        yaw = wrapAngle(yaw, targetYaw, smoothFactor);
        pitch = pitch + (clampedTargetPitch - pitch) * smoothFactor;

        applyFlyMovement(config, deltaTicks);
    }

    public boolean applyToCamera(Camera camera) {
        if (!active) {
            return false;
        }
        camera.setPos(cameraPos.x, cameraPos.y, cameraPos.z);
        camera.setRotation((float) yaw, (float) pitch);
        return true;
    }

    public double getFov() {
        return client.options.getFov().getValue();
    }

    public void initialSelectionChanged(SceneObject object, boolean immediate) {
        Vec3d target = extractObjectPosition(object);
        if (target != null) {
            focusInternal(target, immediate);
        }
    }

    public void focusSelection(@Nullable SceneObject object) {
        if (!active) {
            return;
        }
        Vec3d target = object != null ? extractObjectPosition(object) : null;
        if (target == null) {
            if (client.player != null) {
                target = client.player.getPos();
            } else {
                return;
            }
        }
        focusPoint(target);
    }

    public void focusPoint(@Nullable Vec3d target) {
        if (!active || target == null) {
            return;
        }
        focusInternal(target, true);
    }

    public boolean handleMouseButton(int button, int action, int mods, double cursorX, double cursorY) {
        if (!active) {
            return false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW.GLFW_PRESS) {
                rightMouseDown = true;
                lockCursor();
                return true;
            } else if (action == GLFW.GLFW_RELEASE) {
                rightMouseDown = false;
                unlockCursor();
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (action == GLFW.GLFW_PRESS) {
                middleMouseDown = true;
                lockCursor();
                return true;
            } else if (action == GLFW.GLFW_RELEASE) {
                middleMouseDown = false;
                unlockCursor();
                return true;
            }
        }

        return false;
    }

    public boolean handleMouseDelta(double deltaX, double deltaY) {
        if (!active) {
            return false;
        }

        if (rightMouseDown || middleMouseDown) {
            targetYaw -= deltaX * 0.15;
            targetPitch -= deltaY * 0.15;
            targetPitch = MathHelper.clamp(targetPitch, -89.0, 89.0);
            return true;
        }

        return false;
    }

    public boolean handleScroll(double horizontal, double vertical) {

        return false;
    }

    public boolean isCapturingInput() {
        return rightMouseDown || middleMouseDown;
    }

    public boolean handleKeyPress(int key, int action) {
        if (!active) {
            return false;
        }

        return false;
    }

    public Vec3d getCameraPosition() {
        return cameraPos;
    }

    public float getYaw() {
        return (float) yaw;
    }

    public float getPitch() {
        return (float) pitch;
    }

    private void focusInternal(Vec3d target, boolean immediate) {

        if (immediate) {
            cameraPos = target.add(0, 2, 5);
            yaw = targetYaw;
            pitch = targetPitch;
        }
    }

    private Vec3d forwardVector(double yawDegrees, double pitchDegrees) {
        double yawRad = Math.toRadians(yawDegrees);
        double pitchRad = Math.toRadians(pitchDegrees);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3d vector = new Vec3d(x, y, z);
        return vector.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : vector.normalize();
    }

    private Vec3d lerp(Vec3d from, Vec3d to, double delta) {
        return new Vec3d(
                MathHelper.lerp(delta, from.x, to.x),
                MathHelper.lerp(delta, from.y, to.y),
                MathHelper.lerp(delta, from.z, to.z)
        );
    }

    private double wrapAngle(double current, double target, double percent) {
        double delta = MathHelper.wrapDegrees(target - current);
        return current + delta * percent;
    }

    private void applyFlyMovement(EditorConfig.Camera config, double deltaTicks) {

        if (!rightMouseDown && !middleMouseDown) {
            return;
        }
        if (deltaTicks <= 0.0) {
            return;
        }

        GameOptions options = client.options;
        if (options == null) {
            return;
        }
        Vec3d move = Vec3d.ZERO;

        Vec3d forward = forwardVector(targetYaw, 0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vec3d(0, 0, 1);
        }
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vec3d(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3d up = new Vec3d(0, 1, 0);

        if (options.forwardKey.isPressed()) move = move.add(forward);
        if (options.backKey.isPressed()) move = move.subtract(forward);
        if (options.leftKey.isPressed()) move = move.subtract(right);
        if (options.rightKey.isPressed()) move = move.add(right);
        if (options.jumpKey.isPressed()) move = move.add(up);
        if (options.sneakKey.isPressed()) move = move.subtract(up);

        if (move.lengthSquared() == 0) {
            return;
        }

        double speed = config.flySpeed;
        if (options.sprintKey.isPressed()) {
            speed *= config.flyBoostMultiplier;
        }

        Vec3d movement = move.normalize().multiply(speed * deltaTicks);

        cameraPos = cameraPos.add(movement);
    }

    private void lockCursor() {
        if (client == null || client.currentScreen != null) {
            return;
        }
        if (!client.mouse.isCursorLocked()) {
            client.mouse.lockCursor();
        }
        cursorLocked = true;
    }

    private void unlockCursor() {
        if (client == null) {
            return;
        }
        if (client.mouse.isCursorLocked()) {
            client.mouse.unlockCursor();
        }
        cursorLocked = false;
    }

    private Vec3d extractObjectPosition(SceneObject object) {
        Object value = object.getProperties().get("position");
        if (!(value instanceof Map<?,?> map)) {
            return null;
        }
        Object x = map.get("x");
        Object y = map.get("y");
        Object z = map.get("z");
        if (x instanceof Number && y instanceof Number && z instanceof Number) {
            return new Vec3d(((Number) x).doubleValue(), ((Number) y).doubleValue(), ((Number) z).doubleValue());
        }
        return null;
    }
}