package com.moud.client.api.service;

import com.moud.client.MoudClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import com.moud.client.editor.config.EditorConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.moud.api.math.Vector3;

import java.util.Map;

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
    private boolean animationFollow = false;
    private long lastAnimationUpdateMs = 0;
    private double followSmoothing = 0.85;

    private boolean wasInFirstPerson = false;
    private boolean isAnimating = false;
    private String activeCameraId = null;

    public CameraService() {
        this.client = MinecraftClient.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void updateCamera(float tickDelta) {
        if (!isCustomCameraActive()) {
            return;
        }

        if (isAnimating) {
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
            currentState.yaw = MathHelper.lerpAngleDegrees((float)easedProgress, (float)startState.yaw, (float)targetState.yaw);
            currentState.pitch = MathHelper.lerp(easedProgress, startState.pitch, targetState.pitch);
            currentState.roll = MathHelper.lerpAngleDegrees((float)easedProgress, (float)startState.roll, (float)targetState.roll);
            currentState.fov = MathHelper.lerp(easedProgress, startState.fov, targetState.fov);

            if (progress >= 1.0) {
                isAnimating = false;
                easingFunction = null;
            }
        }

        if (animationFollow) {
            double smoothing = MathHelper.clamp(followSmoothing, 0.01, 0.999);
            double deltaTicks = MathHelper.clamp(tickDelta * 20.0, 0.0, 5.0);
            double alpha = 1.0 - Math.pow(1.0 - smoothing, deltaTicks);

            currentState.position.set(
                    MathHelper.lerp(alpha, currentState.position.x, targetState.position.x),
                    MathHelper.lerp(alpha, currentState.position.y, targetState.position.y),
                    MathHelper.lerp(alpha, currentState.position.z, targetState.position.z)
            );
            currentState.yaw = MathHelper.lerpAngleDegrees((float) alpha, (float) currentState.yaw, (float) targetState.yaw);
            currentState.pitch = MathHelper.lerp(alpha, currentState.pitch, targetState.pitch);
            currentState.roll = MathHelper.lerpAngleDegrees((float) alpha, (float) currentState.roll, (float) targetState.roll);
            currentState.fov = MathHelper.lerp(alpha, currentState.fov, targetState.fov);
        }
    }

    public String getActiveCameraId() {
        return this.activeCameraId;
    }

    @HostAccess.Export
    public void enableCustomCamera() {
        this.enableCustomCamera(null);
    }

    public void enableCustomCamera(String cameraId) {
        if (isCustomCameraActive()) {
            if (cameraId != null && !cameraId.equals(this.activeCameraId)) {
                this.activeCameraId = cameraId;
            }
            return;
        }

        this.activeCameraId = cameraId;
        this.wasInFirstPerson = client.options.getPerspective() == Perspective.FIRST_PERSON;
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);

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
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
        this.wasInFirstPerson = false;
        MoudClientMod.setCustomCameraActive(false);
        isAnimating = false;
        this.activeCameraId = null;
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
            double x = extractDouble(pos, "x", targetState.position.x);
            double y = extractDouble(pos, "y", targetState.position.y);
            double z = extractDouble(pos, "z", targetState.position.z);
            targetState.position.set(x, y, z);
        }
        if (options.hasMember("yaw")) targetState.yaw = extractDouble(options, "yaw", targetState.yaw);
        if (options.hasMember("pitch")) targetState.pitch = extractDouble(options, "pitch", targetState.pitch);
        if (options.hasMember("roll")) targetState.roll = extractDouble(options, "roll", targetState.roll);
        if (options.hasMember("fov")) targetState.fov = extractDouble(options, "fov", targetState.fov);

        this.transitionDuration = options.hasMember("duration") ? options.getMember("duration").asLong() : 1000L;
        this.easingFunction = options.getMember("easing");
        this.transitionStartTime = System.currentTimeMillis();
        this.isAnimating = true;
    }


    public void animateFromAnimation(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        boolean instant = Boolean.TRUE.equals(options.get("instant"));
        if (instant) {
            snapToFromMap(options);
            return;
        }
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        try {
            EditorConfig.Camera cfg = EditorConfig.getInstance().camera();
            followSmoothing = cfg != null ? cfg.orbitSmoothing : followSmoothing;
        } catch (Exception ignored) {
        }

        if (options.containsKey("position")) {
            Object posObj = options.get("position");
            if (posObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> posMap = (Map<String, Object>) posObj;
                targetState.position.set(
                        toDouble(posMap.get("x"), targetState.position.x),
                        toDouble(posMap.get("y"), targetState.position.y),
                        toDouble(posMap.get("z"), targetState.position.z)
                );
            }
        }
        if (options.containsKey("yaw")) targetState.yaw = toDouble(options.get("yaw"), targetState.yaw);
        if (options.containsKey("pitch")) targetState.pitch = toDouble(options.get("pitch"), targetState.pitch);
        if (options.containsKey("roll")) targetState.roll = toDouble(options.get("roll"), targetState.roll);
        if (options.containsKey("fov")) targetState.fov = toDouble(options.get("fov"), targetState.fov);

        lastAnimationUpdateMs = System.currentTimeMillis();
        animationFollow = true;
        isAnimating = false;
    }

    @HostAccess.Export
    public void snapTo(Value options) {
        if (!options.hasMembers()) return;
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        isAnimating = false;

        if (options.hasMember("position")) {
            Value pos = options.getMember("position");
            double x = extractDouble(pos, "x", currentState.position.x);
            double y = extractDouble(pos, "y", currentState.position.y);
            double z = extractDouble(pos, "z", currentState.position.z);
            targetState.position.set(x, y, z);
            currentState.position.set(x, y, z);
            //LOGGER.info("Camera snapTo position -> {} {} {}", x, y, z);
        }
        if (options.hasMember("yaw")) {
            double yaw = extractDouble(options, "yaw", targetState.yaw);
            targetState.yaw = yaw;
            currentState.yaw = yaw;
            //LOGGER.info("Camera snapTo yaw -> {}", yaw);
        }
        if (options.hasMember("pitch")) {
            double pitch = extractDouble(options, "pitch", targetState.pitch);
            targetState.pitch = pitch;
            currentState.pitch = pitch;
            //LOGGER.info("Camera snapTo pitch -> {}", pitch);
        }
        if (options.hasMember("roll")) {
            double roll = extractDouble(options, "roll", targetState.roll);
            targetState.roll = roll;
            currentState.roll = roll;
            //LOGGER.info("Camera snapTo roll -> {}", roll);
        }
        if (options.hasMember("fov")) {
            double fov = extractDouble(options, "fov", targetState.fov);
            targetState.fov = fov;
            currentState.fov = fov;
            //LOGGER.info("Camera snapTo fov -> {}", fov);
        }
    }

    public void snapToFromMap(Map<String, Object> options) {
        if (options == null || options.isEmpty()) return;
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        isAnimating = false;

        if (options.containsKey("position")) {
            Object posObj = options.get("position");
            if (posObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> posMap = (Map<String, Object>) posObj;
                double x = toDouble(posMap.get("x"), currentState.position.x);
                double y = toDouble(posMap.get("y"), currentState.position.y);
                double z = toDouble(posMap.get("z"), currentState.position.z);
                targetState.position.set(x, y, z);
                currentState.position.set(x, y, z);
                //LOGGER.info("Camera snapToFromMap position -> {} {} {}", x, y, z);
            }
        }
        if (options.containsKey("yaw")) {
            double yaw = toDouble(options.get("yaw"), targetState.yaw);
            targetState.yaw = yaw;
            currentState.yaw = yaw;
            //LOGGER.info("Camera snapToFromMap yaw -> {}", yaw);
        }
        if (options.containsKey("pitch")) {
            double pitch = toDouble(options.get("pitch"), targetState.pitch);
            targetState.pitch = pitch;
            currentState.pitch = pitch;
            //LOGGER.info("Camera snapToFromMap pitch -> {}", pitch);
        }
        if (options.containsKey("roll")) {
            double roll = toDouble(options.get("roll"), targetState.roll);
            targetState.roll = roll;
            currentState.roll = roll;
            //LOGGER.info("Camera snapToFromMap roll -> {}", roll);
        }
        if (options.containsKey("fov")) {
            double fov = toDouble(options.get("fov"), targetState.fov);
            targetState.fov = fov;
            currentState.fov = fov;
            //LOGGER.info("Camera snapToFromMap fov -> {}", fov);
        }
    }

    public void transitionToFromMap(Map<String, Object> options) {
        if (options == null || options.isEmpty()) return;
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        isAnimating = false;

        startState.position.set(currentState.position);
        startState.yaw = currentState.yaw;
        startState.pitch = currentState.pitch;
        startState.roll = currentState.roll;
        startState.fov = currentState.fov;

        if (options.containsKey("position")) {
            Object posObj = options.get("position");
            if (posObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> posMap = (Map<String, Object>) posObj;
                double x = toDouble(posMap.get("x"), targetState.position.x);
                double y = toDouble(posMap.get("y"), targetState.position.y);
                double z = toDouble(posMap.get("z"), targetState.position.z);
                targetState.position.set(x, y, z);
            }
        }
        if (options.containsKey("yaw")) targetState.yaw = toDouble(options.get("yaw"), targetState.yaw);
        if (options.containsKey("pitch")) targetState.pitch = toDouble(options.get("pitch"), targetState.pitch);
        if (options.containsKey("roll")) targetState.roll = toDouble(options.get("roll"), targetState.roll);
        if (options.containsKey("fov")) targetState.fov = toDouble(options.get("fov"), targetState.fov);

        this.transitionDuration = options.containsKey("duration") ? ((Number) options.get("duration")).longValue() : 1000L;
        this.easingFunction = null;
        this.transitionStartTime = System.currentTimeMillis();
        this.isAnimating = true;
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return fallback;
    }

    private double extractDouble(Value container, String key, double fallback) {
        try {
            if (container == null) return fallback;

            if (container.hasMember(key)) {
                Value member = container.getMember(key);
                if (member != null && member.isNumber()) {
                    return member.asDouble();
                }
                if (member != null && member.isHostObject()) {
                    Object hostObj = member.asHostObject();
                    if (hostObj instanceof Number) {
                        return ((Number) hostObj).doubleValue();
                    }
                }
            }

            if (container.isHostObject()) {
                Object hostObj = container.asHostObject();
                if (hostObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) hostObj;
                    Object value = map.get(key);
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract double for key '{}': {}", key, e.getMessage());
        }
        return fallback;
    }

    public Vector3d getPosition() { return isCustomCameraActive() ? currentState.position : null; }
    public Float getYaw() { return isCustomCameraActive() ? (float)currentState.yaw : null; }
    public Float getPitch() { return isCustomCameraActive() ? (float)currentState.pitch : null; }
    public Float getRoll() { return isCustomCameraActive() ? (float)currentState.roll : null; }
    public Double getFovInternal() { return isCustomCameraActive() ? currentState.fov : null; }
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
