package com.moud.client.api.service;

import com.moud.api.math.Vector3;
import com.moud.client.MoudClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CameraService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraService.class);
    private final MinecraftClient client;
    private Context jsContext;

    private static class CameraState {
        public Vector3d position = new Vector3d();
        public double yaw = 0, pitch = 0, roll = 0;
        public double fov = 70;
    }

    private final CameraState currentState = new CameraState();
    private final CameraState targetState = new CameraState();
    private final CameraState startState = new CameraState();

    private long transitionStartTime = 0;
    private long transitionDuration = 0;
    private Value easingFunction = null;

    private boolean wasInFirstPerson = false;
    private boolean isAnimating = false;

    public CameraService() {
        this.client = MinecraftClient.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void updateCamera(float tickDelta) {
        if (!isCustomCameraActive() || !isAnimating) {
            return;
        }

        long elapsedTime = System.currentTimeMillis() - transitionStartTime;
        double progress = Math.min((double) elapsedTime / transitionDuration, 1.0);

        double easedProgress;
        if (easingFunction != null && easingFunction.canExecute()) {
            try {
                easedProgress = easingFunction.execute(progress).asDouble();
            } catch (Exception e) {
                LOGGER.warn("Error executing JS easing function", e);
                easedProgress = 1 - Math.pow(1 - progress, 4);
            }
        } else {
            easedProgress = 1 - Math.pow(1 - progress, 4);
        }

        currentState.position.set(
                MathHelper.lerp(easedProgress, startState.position.x, targetState.position.x),
                MathHelper.lerp(easedProgress, startState.position.y, targetState.position.y),
                MathHelper.lerp(easedProgress, startState.position.z, targetState.position.z)
        );
        currentState.yaw = MathHelper.lerp(easedProgress, startState.yaw, targetState.yaw);
        currentState.pitch = MathHelper.lerp(easedProgress, startState.pitch, targetState.pitch);
        currentState.roll = MathHelper.lerp(easedProgress, startState.roll, targetState.roll);
        currentState.fov = MathHelper.lerp(easedProgress, startState.fov, targetState.fov);

        if (progress >= 1.0) {
            isAnimating = false;
            easingFunction = null;
        }
    }

    @HostAccess.Export
    public void enableCustomCamera() {
        if (isCustomCameraActive()) return;

        this.wasInFirstPerson = !isThirdPerson();
        if (this.wasInFirstPerson) {
            setThirdPerson(true);
        }

        Entity camEntity = client.getCameraEntity();
        if (camEntity != null) {
            currentState.position.set(camEntity.getX(), camEntity.getEyeY(), camEntity.getZ());
            currentState.yaw = camEntity.getYaw();
            currentState.pitch = camEntity.getPitch();
            currentState.roll = 0;
            currentState.fov = client.options.getFov().getValue();

            targetState.position.set(currentState.position);
            targetState.yaw = currentState.yaw;
            targetState.pitch = currentState.pitch;
            targetState.roll = currentState.roll;
            targetState.fov = currentState.fov;
        }
        MoudClientMod.setCustomCameraActive(true);
    }

    @HostAccess.Export
    public void disableCustomCamera() {
        if (!isCustomCameraActive()) return;

        if (this.wasInFirstPerson) {
            setThirdPerson(false);
        }
        this.wasInFirstPerson = false;
        MoudClientMod.setCustomCameraActive(false);
        isAnimating = false;
    }

    @HostAccess.Export
    public void transitionTo(Value options) {
        if (!isCustomCameraActive() || !options.hasMembers()) return;

        isAnimating = false;

        startState.position.set(currentState.position);
        startState.yaw = currentState.yaw;
        startState.pitch = currentState.pitch;
        startState.roll = currentState.roll;
        startState.fov = currentState.fov;

        if (options.hasMember("position")) {
            Value pos = options.getMember("position");
            targetState.position.set(pos.getMember("x").asDouble(), pos.getMember("y").asDouble(), pos.getMember("z").asDouble());
        }
        if (options.hasMember("yaw")) targetState.yaw = options.getMember("yaw").asDouble();
        if (options.hasMember("pitch")) targetState.pitch = options.getMember("pitch").asDouble();
        if (options.hasMember("roll")) targetState.roll = options.getMember("roll").asDouble();
        if (options.hasMember("fov")) targetState.fov = options.getMember("fov").asDouble();

        this.transitionDuration = options.hasMember("duration") ? options.getMember("duration").asLong() : 1000L;
        this.easingFunction = options.getMember("easing");
        this.transitionStartTime = System.currentTimeMillis();
        this.isAnimating = true;
    }

    @HostAccess.Export
    public void snapTo(Value options) {
        if (!isCustomCameraActive() || !options.hasMembers()) return;

        isAnimating = false;

        if (options.hasMember("position")) {
            Value pos = options.getMember("position");
            currentState.position.set(pos.getMember("x").asDouble(), pos.getMember("y").asDouble(), pos.getMember("z").asDouble());
        }
        if (options.hasMember("yaw")) currentState.yaw = options.getMember("yaw").asDouble();
        if (options.hasMember("pitch")) currentState.pitch = options.getMember("pitch").asDouble();
        if (options.hasMember("roll")) currentState.roll = options.getMember("roll").asDouble();
        if (options.hasMember("fov")) currentState.fov = options.getMember("fov").asDouble();

        targetState.position.set(currentState.position);
        targetState.yaw = currentState.yaw;
        targetState.pitch = currentState.pitch;
        targetState.roll = currentState.roll;
        targetState.fov = currentState.fov;
    }

    public Vector3d getPosition() { return isCustomCameraActive() ? currentState.position : null; }
    public Float getYaw() { return isCustomCameraActive() ? (float)currentState.yaw : null; }
    public Float getPitch() { return isCustomCameraActive() ? (float)currentState.pitch : null; }
    public Float getRoll() { return isCustomCameraActive() ? (float)currentState.roll : null; }
    public Double getFovInternal() { return isCustomCameraActive() ? currentState.fov : null; } // Renamed for clarity
    public boolean shouldDisableViewBobbing() { return isCustomCameraActive(); }
    @HostAccess.Export
    public boolean isCustomCameraActive() {
        return MoudClientMod.isCustomCameraActive();
    }

    @HostAccess.Export
    public double getPlayerX() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getX() : 0.0;
    }

    @HostAccess.Export
    public double getPlayerY() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getEyeY() : 0.0;
    }

    @HostAccess.Export
    public double getPlayerZ() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getZ() : 0.0;
    }

    @HostAccess.Export
    public float getPlayerYaw() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getYaw() : 0.0f;
    }

    @HostAccess.Export
    public float getPlayerPitch() {
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getPitch() : 0.0f;
    }

    @HostAccess.Export
    public Vector3 createVector3(double x, double y, double z) {
        return new Vector3(x, y, z);
    }

    @HostAccess.Export
    public boolean isThirdPerson() {
        return client.options.getPerspective() != Perspective.FIRST_PERSON;
    }

    @HostAccess.Export
    public void setThirdPerson(boolean thirdPerson) {
        Perspective current = client.options.getPerspective();
        boolean isCurrentlyThirdPerson = current != Perspective.FIRST_PERSON;

        if (thirdPerson && !isCurrentlyThirdPerson) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!thirdPerson && isCurrentlyThirdPerson) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
    }
    @HostAccess.Export
    public double getFov() {
        return client.options.getFov().getValue();
    }

    public void cleanUp() {
        this.jsContext = null;
        if (MoudClientMod.isCustomCameraActive()) {
            this.disableCustomCamera();
        }
        LOGGER.info("CameraService cleaned up.");
    }
}