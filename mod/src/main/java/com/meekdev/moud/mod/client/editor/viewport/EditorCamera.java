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
    private static final float MAX_PITCH = 89.99f;
    private static final float FRAME_DURATION_SECONDS = 0.2f;
    private static final double MINIMUM_FOCUS_DISTANCE = 0.75;
    private static final double DOLLY_STEP_FRACTION = 0.15;
    private static final double ZOOM_STEP = 0.5;
    private static final double ORTHO_BACK = 600.0;
    private static final float ORTHO_DEPTH = 2400.0f;
    private static final double ORTHO_ZOOM_FACTOR = 0.88;
    private static final double MIN_ORTHO_HALF = 0.5;
    private static final double MAX_ORTHO_HALF = 1000.0;
    private static final float ALIGN_DURATION_SECONDS = 0.25f;

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
    private boolean orthographic;
    private double orthoHalfHeight = 12.0;
    private boolean aligning;
    private float alignElapsed;
    private float alignFromYaw;
    private float alignFromPitch;
    private float alignToYaw;
    private float alignToPitch;

    public void placeAt(Vec3 eye, float yawDegrees, float pitchDegrees) {
        position.set(eye.x, eye.y, eye.z);
        yaw = yawDegrees;
        pitch = Math.clamp(pitchDegrees, -MAX_PITCH, MAX_PITCH);
        framing = false;
        forward(focusPoint).mul(focusDistance).add(position);
    }

    public void apply() {
        if (orthographic) {
            Vector3d back = forward(new Vector3d()).mul(-ORTHO_BACK).add(position);
            AmneticCamera.setOrthographic((float) orthoHalfHeight, ORTHO_DEPTH);
            AmneticCamera.setPose(new Vec3(back.x, back.y, back.z), yaw, pitch);
        } else {
            AmneticCamera.clearOrthographic();
            AmneticCamera.setPose(new Vec3(position.x, position.y, position.z), yaw, pitch);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && EditorBody.editing(player)) {
            player.setPos(position.x, position.y - player.getEyeHeight(), position.z);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    public void release() {
        AmneticCamera.clearOrthographic();
    }

    public boolean orthographic() {
        return orthographic;
    }

    public void toggleOrthographic() {
        orthographic = !orthographic;
        if (orthographic) {
            double distance = Math.max(MINIMUM_FOCUS_DISTANCE, position.distance(focusPoint));
            orthoHalfHeight = Math.clamp(distance * Math.tan(Math.toRadians(AmneticCamera.fov()) * 0.5), MIN_ORTHO_HALF, MAX_ORTHO_HALF);
        }
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void alignTo(double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-9) return;
        dx /= length;
        dy /= length;
        dz /= length;
        double distance = Math.max(MINIMUM_FOCUS_DISTANCE, position.distance(focusPoint));
        focusDistance = distance;
        alignFromYaw = yaw;
        alignFromPitch = pitch;
        alignToPitch = (float) Math.clamp(-Math.toDegrees(Math.asin(dy)), -89.99, 89.99);
        float targetYaw = Math.abs(dy) > 0.999 ? yaw : (float) Math.toDegrees(Math.atan2(-dx, dz));
        float turn = ((targetYaw - yaw) % 360 + 540) % 360 - 180;
        alignToYaw = yaw + turn;
        alignElapsed = 0;
        aligning = true;
        framing = false;
    }

    public void updateAligning(float deltaSeconds) {
        if (!aligning) return;
        alignElapsed += deltaSeconds;
        float progress = Math.min(1.0f, alignElapsed / ALIGN_DURATION_SECONDS);
        float eased = progress * progress * (3.0f - 2.0f * progress);
        yaw = alignFromYaw + (alignToYaw - alignFromYaw) * eased;
        pitch = alignFromPitch + (alignToPitch - alignFromPitch) * eased;
        position.set(forward(new Vector3d()).mul(-focusDistance).add(focusPoint));
        if (progress >= 1.0f) aligning = false;
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
        aligning = false;
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
        if (orthographic) {
            orthoHalfHeight = Math.clamp(orthoHalfHeight * Math.pow(ORTHO_ZOOM_FACTOR, wheel), MIN_ORTHO_HALF, MAX_ORTHO_HALF);
            return;
        }
        position.add(forward(new Vector3d()).mul(ZOOM_STEP * wheel));
    }

    public void applyDolly(float wheel) {
        if (wheel == 0.0f) return;
        if (orthographic) {
            applyZoom(wheel);
            return;
        }
        framing = false;
        double remaining = Math.max(MINIMUM_FOCUS_DISTANCE, position.distance(focusPoint));
        double step = remaining * DOLLY_STEP_FRACTION * wheel;
        position.add(forward(new Vector3d()).mul(step));
        focusDistance = Math.max(MINIMUM_FOCUS_DISTANCE, focusDistance - step);
    }

    public void frame(Vector3d center, double radius) {
        double halfFov = Math.toRadians(AmneticCamera.fov()) * 0.5;
        double distance = Math.max(MINIMUM_FOCUS_DISTANCE, radius / Math.tan(halfFov) * 1.4);
        if (orthographic) orthoHalfHeight = Math.clamp(radius * 1.4, MIN_ORTHO_HALF, MAX_ORTHO_HALF);
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
