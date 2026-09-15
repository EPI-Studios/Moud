package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.moud.mod.adapter.player.EditorBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class EditorCamera {

    private static final float LOOK_DEGREES_PER_PIXEL = 0.2f;
    private static final float MOVE_SPEED = 12.0f;
    private static final float BOOST_MULTIPLIER = 3.0f;
    private static final float MAX_PITCH = 89.0f;
    private static final float FRAME_DURATION_SECONDS = 0.2f;
    private static final double MINIMUM_FOCUS_DISTANCE = 0.75;
    private static final double DOLLY_STEP_FRACTION = 0.15;
    private static final double ZOOM_STEP = 0.5;

    private final Vector3d position = new Vector3d();
    private final Vector3d focusPoint = new Vector3d();
    private final Vector3d frameStart = new Vector3d();
    private final Vector3d frameTarget = new Vector3d();
    private float yaw;
    private float pitch;
    private double focusDistance = 10.0;
    private float frameElapsed;
    private boolean framing;
    private boolean lookActive;
    private boolean orbitActive;
    private float lastMouseX;
    private float lastMouseY;

    public void placeAt(Vec3 eye, float yawDegrees, float pitchDegrees) {
        position.set(eye.x, eye.y, eye.z);
        yaw = yawDegrees;
        pitch = Math.clamp(pitchDegrees, -MAX_PITCH, MAX_PITCH);
        framing = false;
        forward(focusPoint).mul(focusDistance).add(position);
    }

    public void apply() {
        AmneticCamera.setPose(new Vec3(position.x, position.y, position.z), yaw, pitch);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && EditorBody.editing(player)) {
            player.setPos(position.x, position.y - player.getEyeHeight(), position.z);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    public Vector3d position() {
        return position;
    }

    public double focusDistance() {
        return focusDistance;
    }

    public Vector3d forward(Vector3d destination) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        return destination.set(-Math.sin(yawRadians) * cosPitch, -Math.sin(pitchRadians), Math.cos(yawRadians) * cosPitch);
    }

    private Vector3d right(Vector3d destination) {
        double yawRadians = Math.toRadians(yaw);
        return destination.set(-Math.cos(yawRadians), 0.0, -Math.sin(yawRadians));
    }

    public void updateLook(float mouseX, float mouseY, boolean held) {
        if (!held) {
            lookActive = false;
            return;
        }
        if (!lookActive) {
            lookActive = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return;
        }
        turn(mouseX, mouseY);
        framing = false;
    }

    public void updateOrbit(float mouseX, float mouseY, boolean held) {
        if (!held) {
            orbitActive = false;
            return;
        }
        if (!orbitActive) {
            orbitActive = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            framing = false;
            focusDistance = Math.max(MINIMUM_FOCUS_DISTANCE, position.distance(focusPoint));
            return;
        }
        turn(mouseX, mouseY);
        Vector3d back = forward(new Vector3d()).mul(-focusDistance);
        position.set(focusPoint).add(back);
    }

    private void turn(float mouseX, float mouseY) {
        yaw += (mouseX - lastMouseX) * LOOK_DEGREES_PER_PIXEL;
        pitch = Math.clamp(pitch + (mouseY - lastMouseY) * LOOK_DEGREES_PER_PIXEL, -MAX_PITCH, MAX_PITCH);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public void updateMovement(boolean forwards, boolean backwards, boolean left, boolean right, boolean up, boolean down,
                               boolean boost, float deltaSeconds) {
        if (!lookActive) return;
        Vector3d ahead = forward(new Vector3d());
        Vector3d side = right(new Vector3d());
        Vector3d movement = new Vector3d();
        if (forwards) movement.add(ahead);
        if (backwards) movement.sub(ahead);
        if (right) movement.add(side);
        if (left) movement.sub(side);
        if (up) movement.add(0.0, 1.0, 0.0);
        if (down) movement.sub(0.0, 1.0, 0.0);
        if (movement.lengthSquared() <= 0.0) return;
        double speed = boost ? MOVE_SPEED * BOOST_MULTIPLIER : MOVE_SPEED;
        position.add(movement.normalize().mul(speed * deltaSeconds));
    }

    public void applyZoom(float wheel) {
        if (wheel == 0.0f) return;
        position.add(forward(new Vector3d()).mul(ZOOM_STEP * wheel));
    }

    public void applyDolly(float wheel) {
        if (wheel == 0.0f) return;
        framing = false;
        double remaining = Math.max(MINIMUM_FOCUS_DISTANCE, position.distance(focusPoint));
        double step = remaining * DOLLY_STEP_FRACTION * wheel;
        position.add(forward(new Vector3d()).mul(step));
        focusDistance = Math.max(MINIMUM_FOCUS_DISTANCE, focusDistance - step);
    }

    public void frame(Vector3d center, double radius) {
        double halfFov = Math.toRadians(AmneticCamera.fov()) * 0.5;
        double distance = Math.max(MINIMUM_FOCUS_DISTANCE, radius / Math.tan(halfFov) * 1.4);
        focusPoint.set(center);
        focusDistance = distance;
        frameStart.set(position);
        frameTarget.set(center).sub(forward(new Vector3d()).mul(distance));
        frameElapsed = 0.0f;
        framing = true;
    }

    public void updateFraming(float deltaSeconds) {
        if (!framing) return;
        frameElapsed += deltaSeconds;
        float progress = Math.min(1.0f, frameElapsed / FRAME_DURATION_SECONDS);
        float eased = progress * progress * (3.0f - 2.0f * progress);
        position.set(frameStart).lerp(frameTarget, eased);
        if (progress >= 1.0f) framing = false;
    }
}
