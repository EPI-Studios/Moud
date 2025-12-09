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

import java.util.ArrayList;
import java.util.List;
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

    private final Vector3d currentVelocity = new Vector3d(0, 0, 0);
    private double smoothTime = 0.1;
    private double maxSpeed = Double.POSITIVE_INFINITY;
    private boolean useSmoothDamp = false;

    private long transitionStartTime = 0;
    private long transitionDuration = 0;
    private Value easingFunction = null;
    private boolean animationFollow = false;
    private long lastAnimationUpdateMs = 0;
    private double followSmoothing = 0.85;

    private boolean wasInFirstPerson = false;
    private boolean isAnimating = false;
    private String activeCameraId = null;

    private static class PathPoint {
        Vector3d position;
        double yaw, pitch, roll, fov;
        PathPoint(Vector3d pos, double y, double p, double r, double f) {
            position = pos; yaw = y; pitch = p; roll = r; fov = f;
        }
    }
    private List<PathPoint> pathPoints = null;
    private boolean pathLoop = false;
    private long pathStartTime = 0;
    private long pathDuration = 0;

    private static class CinematicKeyframe {
        Vector3d position;
        double yaw, pitch, roll, fov;
        long duration;
        CinematicKeyframe(Vector3d pos, double y, double p, double r, double f, long dur) {
            position = pos; yaw = y; pitch = p; roll = r; fov = f; duration = dur;
        }
    }
    private List<CinematicKeyframe> cinematicKeyframes = null;
    private int currentKeyframeIndex = 0;
    private long keyframeStartTime = 0;

    private Entity lookAtEntity = null;
    private Vector3d lookAtPosition = null;

    private boolean dollyZoomActive = false;
    private long dollyZoomStartTime = 0;
    private long dollyZoomDuration = 0;
    private double dollyZoomStartFov = 70;
    private double dollyZoomTargetFov = 70;
    private double dollyZoomStartDistance = 0;
    private boolean dollyTrackDirection = false;
    private boolean dollyUseCameraOrientation = true;
    private double dollyDirectionYaw = 0;
    private double dollyDirectionPitch = 0;
    private double dollyDirectionDistance = 6.0;
    private static class DollyZoomConfig {
        double targetFov = 70;
        long duration = 1000;
        double distance = 6.0;
        boolean alignCamera = true;
        boolean maintainTarget = true;
        boolean usePlayerOrientation = true;
        Vector3d targetPosition = null;
        Double directionYaw = null;
        Double directionPitch = null;
    }

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

        if (cinematicKeyframes != null && !cinematicKeyframes.isEmpty()) {
            updateCinematic();
        }

        if (pathPoints != null && !pathPoints.isEmpty()) {
            updatePath();
        }

        if (dollyZoomActive) {
            updateDollyZoom();
        }

        if (lookAtEntity != null || lookAtPosition != null) {
            updateLookAt();
        }

        if (useSmoothDamp) {
            double dt = Math.min(tickDelta * 0.05, 0.1);

            smoothDampVector(currentState.position, targetState.position, currentVelocity, smoothTime, maxSpeed, dt);

            double t = 1.0 - Math.pow(0.01, dt);
            currentState.yaw = MathHelper.lerpAngleDegrees((float) t, (float) currentState.yaw, (float) targetState.yaw);
            currentState.pitch = MathHelper.lerp(t, currentState.pitch, targetState.pitch);
            currentState.roll = MathHelper.lerpAngleDegrees((float) t, (float) currentState.roll, (float) targetState.roll);
            currentState.fov = MathHelper.lerp(t, currentState.fov, targetState.fov);
        } else if (isAnimating) {
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
        } else if (animationFollow) {
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

        if (client.player != null) {
            client.player.setInvisible(false);
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
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
        this.wasInFirstPerson = false;
        MoudClientMod.setCustomCameraActive(false);
        isAnimating = false;
        useSmoothDamp = false;
        currentVelocity.set(0, 0, 0);
        this.activeCameraId = null;
    }

    @HostAccess.Export
    public void transitionTo(Value options) {
        if (!isCustomCameraActive() || !options.hasMembers()) return;

        isAnimating = false;
        useSmoothDamp = false;

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

        // Prefer rich camera animations when provided by editor/runtime.
        if (options.containsKey("keyframes")) {
            Object kf = options.get("keyframes");
            if (kf instanceof List<?> list && !list.isEmpty()) {
                createCinematicFromList(list);
                return;
            }
        }
        if (options.containsKey("path")) {
            Object path = options.get("path");
            if (path instanceof List<?> list && !list.isEmpty()) {
                long duration = options.containsKey("duration") ? toLong(options.get("duration"), 0L) : 0L;
                boolean loop = Boolean.TRUE.equals(options.get("loop"));
                followPathFromList(list, duration, loop);
                return;
            }
        }
        if (options.containsKey("points")) {
            Object path = options.get("points");
            if (path instanceof List<?> list && !list.isEmpty()) {
                long duration = options.containsKey("duration") ? toLong(options.get("duration"), 0L) : 0L;
                boolean loop = Boolean.TRUE.equals(options.get("loop"));
                followPathFromList(list, duration, loop);
                return;
            }
        }

        boolean instant = Boolean.TRUE.equals(options.get("instant"));
        if (instant) {
            snapToFromMap(options);
            return;
        }
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        useSmoothDamp = false;
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
        useSmoothDamp = false;
        animationFollow = false; // disable follow mode for instant snap
        currentVelocity.set(0, 0, 0);

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

    @HostAccess.Export
    public void followTo(Value options) {
        if (!options.hasMembers()) return;
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        isAnimating = false;
        useSmoothDamp = false;
        animationFollow = true; // smooth camera

        if (options.hasMember("smoothing")) {
            followSmoothing = MathHelper.clamp(extractDouble(options, "smoothing", 0.85), 0.01, 0.999);
        }

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
    }

    @HostAccess.Export
    public void smoothFollow(Value options) {
        if (!options.hasMembers()) {
            return;
        }
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        isAnimating = false;
        animationFollow = false;
        useSmoothDamp = true;
        currentVelocity.set(0, 0, 0);

        if (options.hasMember("smoothTime")) {
            smoothTime = Math.max(0.0001, extractDouble(options, "smoothTime", 0.1));
        }
        if (options.hasMember("maxSpeed")) {
            maxSpeed = extractDouble(options, "maxSpeed", Double.POSITIVE_INFINITY);
        }

        if (options.hasMember("position")) {
            Value pos = options.getMember("position");
            targetState.position.set(
                    extractDouble(pos, "x", targetState.position.x),
                    extractDouble(pos, "y", targetState.position.y),
                    extractDouble(pos, "z", targetState.position.z)
            );
        }
        if (options.hasMember("yaw")) targetState.yaw = extractDouble(options, "yaw", targetState.yaw);
        if (options.hasMember("pitch")) targetState.pitch = extractDouble(options, "pitch", targetState.pitch);
        if (options.hasMember("roll")) targetState.roll = extractDouble(options, "roll", targetState.roll);
        if (options.hasMember("fov")) targetState.fov = extractDouble(options, "fov", targetState.fov);
    }

    public void snapToFromMap(Map<String, Object> options) {
        if (options == null || options.isEmpty()) return;
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        isAnimating = false;
        useSmoothDamp = false;
        animationFollow = false;
        currentVelocity.set(0, 0, 0);

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

    public void followToFromMap(Map<String, Object> options) {
        if (options == null || options.isEmpty()) return;
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        isAnimating = false;
        useSmoothDamp = false;
        animationFollow = true; // Enable smooth follow mode

        // Optional smoothing parameter (0.01 to 0.999, default 0.85)
        if (options.containsKey("smoothing")) {
            followSmoothing = MathHelper.clamp(toDouble(options.get("smoothing"), 0.85), 0.01, 0.999);
        }

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
    }

    public void transitionToFromMap(Map<String, Object> options) {
        if (options == null || options.isEmpty()) return;
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        isAnimating = false;
        useSmoothDamp = false;

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

    private void smoothDampVector(Vector3d current, Vector3d target, Vector3d velocity,
                                  double desiredSmoothTime, double desiredMaxSpeed, double deltaTime) {
        double clampedSmoothTime = Math.max(0.0001, desiredSmoothTime);
        double omega = 2.0 / clampedSmoothTime;
        double x = omega * deltaTime;
        double exp = 1.0 / (1.0 + x + 0.48 * x * x + 0.235 * x * x * x);

        double changeX = current.x - target.x;
        double changeY = current.y - target.y;
        double changeZ = current.z - target.z;

        double maxChange = desiredMaxSpeed * clampedSmoothTime;
        double changeSq = changeX * changeX + changeY * changeY + changeZ * changeZ;
        if (changeSq > maxChange * maxChange) {
            double scale = maxChange / Math.sqrt(changeSq);
            changeX *= scale;
            changeY *= scale;
            changeZ *= scale;
        }

        double destX = current.x - changeX;
        double destY = current.y - changeY;
        double destZ = current.z - changeZ;

        double tempX = (velocity.x + omega * changeX) * deltaTime;
        double tempY = (velocity.y + omega * changeY) * deltaTime;
        double tempZ = (velocity.z + omega * changeZ) * deltaTime;

        velocity.x = (velocity.x - omega * tempX) * exp;
        velocity.y = (velocity.y - omega * tempY) * exp;
        velocity.z = (velocity.z - omega * tempZ) * exp;

        double outputX = destX + (changeX + tempX) * exp;
        double outputY = destY + (changeY + tempY) * exp;
        double outputZ = destZ + (changeZ + tempZ) * exp;

        if ((target.x - current.x > 0.0) == (outputX > target.x)) {
            outputX = target.x;
            velocity.x = (outputX - target.x) / deltaTime;
        }
        if ((target.y - current.y > 0.0) == (outputY > target.y)) {
            outputY = target.y;
            velocity.y = (outputY - target.y) / deltaTime;
        }
        if ((target.z - current.z > 0.0) == (outputZ > target.z)) {
            outputZ = target.z;
            velocity.z = (outputZ - target.z) / deltaTime;
        }

        current.set(outputX, outputY, outputZ);
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return fallback;
    }

    private long toLong(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return fallback;
    }

    private Vector3d directionFromYawPitch(double yawDeg, double pitchDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        Vector3d dir = new Vector3d(x, y, z);
        if (dir.lengthSquared() < 1.0e-6) {
            dir.set(0, 0, 1);
        }
        dir.normalize();
        return dir;
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


    private void updatePath() {
        long elapsed = System.currentTimeMillis() - pathStartTime;
        double progress = Math.min((double) elapsed / pathDuration, 1.0);

        if (progress >= 1.0) {
            if (pathLoop) {
                pathStartTime = System.currentTimeMillis();
                progress = 0.0;
            } else {
                pathPoints = null;
                return;
            }
        }

        double scaledProgress = progress * (pathPoints.size() - 1);
        int index = (int) scaledProgress;
        double t = scaledProgress - index;

        if (index >= pathPoints.size() - 1) {
            index = pathPoints.size() - 2;
            t = 1.0;
        }

        PathPoint p0 = pathPoints.get(Math.max(0, index - 1));
        PathPoint p1 = pathPoints.get(index);
        PathPoint p2 = pathPoints.get(Math.min(pathPoints.size() - 1, index + 1));
        PathPoint p3 = pathPoints.get(Math.min(pathPoints.size() - 1, index + 2));

        currentState.position.set(
            catmullRom(t, p0.position.x, p1.position.x, p2.position.x, p3.position.x),
            catmullRom(t, p0.position.y, p1.position.y, p2.position.y, p3.position.y),
            catmullRom(t, p0.position.z, p1.position.z, p2.position.z, p3.position.z)
        );
        currentState.yaw = MathHelper.lerpAngleDegrees((float) t, (float) p1.yaw, (float) p2.yaw);
        currentState.pitch = MathHelper.lerp(t, p1.pitch, p2.pitch);
        currentState.roll = MathHelper.lerpAngleDegrees((float) t, (float) p1.roll, (float) p2.roll);
        currentState.fov = MathHelper.lerp(t, p1.fov, p2.fov);
    }

    private double catmullRom(double t, double p0, double p1, double p2, double p3) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1) +
                     (-p0 + p2) * t +
                     (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                     (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private void updateCinematic() {
        if (currentKeyframeIndex >= cinematicKeyframes.size()) {
            cinematicKeyframes = null;
            return;
        }

        CinematicKeyframe current = cinematicKeyframes.get(currentKeyframeIndex);
        long elapsed = System.currentTimeMillis() - keyframeStartTime;
        double progress = Math.min((double) elapsed / current.duration, 1.0);

        if (currentKeyframeIndex == 0) {
            startState.position.set(currentState.position);
            startState.yaw = currentState.yaw;
            startState.pitch = currentState.pitch;
            startState.roll = currentState.roll;
            startState.fov = currentState.fov;
        }

        double easedProgress = 1 - Math.pow(1 - progress, 3);

        currentState.position.set(
            MathHelper.lerp(easedProgress, startState.position.x, current.position.x),
            MathHelper.lerp(easedProgress, startState.position.y, current.position.y),
            MathHelper.lerp(easedProgress, startState.position.z, current.position.z)
        );
        currentState.yaw = MathHelper.lerpAngleDegrees((float) easedProgress, (float) startState.yaw, (float) current.yaw);
        currentState.pitch = MathHelper.lerp(easedProgress, startState.pitch, current.pitch);
        currentState.roll = MathHelper.lerpAngleDegrees((float) easedProgress, (float) startState.roll, (float) current.roll);
        currentState.fov = MathHelper.lerp(easedProgress, startState.fov, current.fov);

        if (progress >= 1.0) {
            currentKeyframeIndex++;
            if (currentKeyframeIndex < cinematicKeyframes.size()) {
                keyframeStartTime = System.currentTimeMillis();
                startState.position.set(current.position);
                startState.yaw = current.yaw;
                startState.pitch = current.pitch;
                startState.roll = current.roll;
                startState.fov = current.fov;
            }
        }
    }

    private void updateLookAt() {
        Vector3d lookTarget = null;

        if (lookAtEntity != null && !lookAtEntity.isRemoved()) {
            lookTarget = new Vector3d(lookAtEntity.getX(), lookAtEntity.getEyeY(), lookAtEntity.getZ());
        } else if (lookAtPosition != null) {
            lookTarget = lookAtPosition;
        }

        if (lookTarget != null) {
            double dx = lookTarget.x - currentState.position.x;
            double dy = lookTarget.y - currentState.position.y;
            double dz = lookTarget.z - currentState.position.z;

            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            currentState.yaw = Math.toDegrees(Math.atan2(-dx, dz));
            currentState.pitch = -Math.toDegrees(Math.atan2(dy, horizontalDist));
        }
    }

    private void updateDollyZoom() {
        Vector3d lookTarget = resolveDollyLookTarget();
        if (lookTarget == null) {
            dollyZoomActive = false;
            return;
        }

        long elapsed = System.currentTimeMillis() - dollyZoomStartTime;
        double progress = Math.min((double) elapsed / dollyZoomDuration, 1.0);

        if (progress >= 1.0) {
            dollyZoomActive = false;
            currentState.fov = dollyZoomTargetFov;
            return;
        }

        double easedProgress = 1 - Math.pow(1 - progress, 3);
        double currentFov = MathHelper.lerp(easedProgress, dollyZoomStartFov, dollyZoomTargetFov);
        currentState.fov = currentFov;

        double dx = lookTarget.x - currentState.position.x;
        double dy = lookTarget.y - currentState.position.y;
        double dz = lookTarget.z - currentState.position.z;
        double currentDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double startFovRad = Math.toRadians(dollyZoomStartFov / 2.0);
        double currentFovRad = Math.toRadians(currentFov / 2.0);
        double newDistance = dollyZoomStartDistance * (Math.tan(currentFovRad) / Math.tan(startFovRad));

        double scale = currentDistance != 0 ? newDistance / currentDistance : 1.0;
        currentState.position.set(
            lookTarget.x - dx * scale,
            lookTarget.y - dy * scale,
            lookTarget.z - dz * scale
        );

        // keep camera orientation facing target
        double horiz = Math.sqrt(dx * dx + dz * dz);
        currentState.yaw = Math.toDegrees(Math.atan2(-dx, dz));
        currentState.pitch = -Math.toDegrees(Math.atan2(dy, horiz));
    }

    @HostAccess.Export
    public void followPath(Value points, long duration, boolean loop) {
        if (!points.hasArrayElements()) {
            LOGGER.warn("followPath requires an array of points");
            return;
        }

        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        pathPoints = new ArrayList<>();
        long size = points.getArraySize();

        for (int i = 0; i < size; i++) {
            Value point = points.getArrayElement(i);
            Vector3d pos = new Vector3d(
                extractDouble(point, "x", 0),
                extractDouble(point, "y", 0),
                extractDouble(point, "z", 0)
            );
            double yaw = extractDouble(point, "yaw", currentState.yaw);
            double pitch = extractDouble(point, "pitch", currentState.pitch);
            double roll = extractDouble(point, "roll", 0);
            double fov = extractDouble(point, "fov", 70);

            pathPoints.add(new PathPoint(pos, yaw, pitch, roll, fov));
        }

        pathDuration = duration;
        pathLoop = loop;
        pathStartTime = System.currentTimeMillis();
        isAnimating = false;
        useSmoothDamp = false;
        animationFollow = false;
    }

    public void followPathFromList(List<?> points, long duration, boolean loop) {
        if (points == null || points.isEmpty()) {
            LOGGER.warn("followPathFromList requires at least one point");
            return;
        }

        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        pathPoints = new ArrayList<>();
        for (Object obj : points) {
            if (!(obj instanceof Map<?, ?> map)) {
                LOGGER.warn("followPathFromList skipped non-map point: {}", obj != null ? obj.getClass().getSimpleName() : "null");
                continue;
            }
            Vector3d pos = new Vector3d(
                    toDouble(map.get("x"), 0),
                    toDouble(map.get("y"), 0),
                    toDouble(map.get("z"), 0)
            );
            double yaw = toDouble(map.get("yaw"), currentState.yaw);
            double pitch = toDouble(map.get("pitch"), currentState.pitch);
            double roll = toDouble(map.get("roll"), 0);
            double fov = toDouble(map.get("fov"), 70);
            pathPoints.add(new PathPoint(pos, yaw, pitch, roll, fov));
        }

        if (pathPoints.isEmpty()) {
            LOGGER.warn("followPathFromList produced no valid points");
            return;
        }

        pathDuration = duration;
        pathLoop = loop;
        pathStartTime = System.currentTimeMillis();
        isAnimating = false;
        useSmoothDamp = false;
        animationFollow = false;
    }

    @HostAccess.Export
    public void createCinematic(Value keyframes) {
        if (!keyframes.hasArrayElements()) {
            LOGGER.warn("createCinematic requires an array of keyframes (hasArrayElements={} memberKeys={} class={})",
                    keyframes.hasArrayElements(),
                    keyframes.hasMembers() ? keyframes.getMemberKeys() : null,
                    keyframes.getClass().getSimpleName());
            return;
        }

        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        cinematicKeyframes = new ArrayList<>();
        long size = keyframes.getArraySize();

        for (int i = 0; i < size; i++) {
            Value kf = keyframes.getArrayElement(i);
            Vector3d pos = new Vector3d(
                extractDouble(kf, "x", 0),
                extractDouble(kf, "y", 0),
                extractDouble(kf, "z", 0)
            );

            if (kf.hasMember("position")) {
                Value posVal = kf.getMember("position");
                pos.set(
                    extractDouble(posVal, "x", 0),
                    extractDouble(posVal, "y", 0),
                    extractDouble(posVal, "z", 0)
                );
            }

            double yaw = extractDouble(kf, "yaw", currentState.yaw);
            double pitch = extractDouble(kf, "pitch", currentState.pitch);
            double roll = extractDouble(kf, "roll", 0);
            double fov = extractDouble(kf, "fov", 70);
            long dur = kf.hasMember("duration") ? kf.getMember("duration").asLong() : 1000;

            cinematicKeyframes.add(new CinematicKeyframe(pos, yaw, pitch, roll, fov, dur));
        }

        currentKeyframeIndex = 0;
        keyframeStartTime = System.currentTimeMillis();
        isAnimating = false;
        useSmoothDamp = false;
        animationFollow = false;
    }

    public void createCinematicFromList(List<?> keyframes) {
        if (keyframes == null || keyframes.isEmpty()) {
            LOGGER.warn("createCinematicFromList requires at least one keyframe");
            return;
        }
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        cinematicKeyframes = new ArrayList<>();
        for (Object obj : keyframes) {
            if (!(obj instanceof Map<?, ?> map)) {
                LOGGER.warn("createCinematicFromList skipped non-map keyframe: {}", obj != null ? obj.getClass().getSimpleName() : "null");
                continue;
            }

            Vector3d pos = new Vector3d(
                    toDouble(map.get("x"), 0),
                    toDouble(map.get("y"), 0),
                    toDouble(map.get("z"), 0)
            );

            Object positionObj = map.get("position");
            if (positionObj instanceof Map<?, ?> posMap) {
                pos.set(
                        toDouble(posMap.get("x"), pos.x),
                        toDouble(posMap.get("y"), pos.y),
                        toDouble(posMap.get("z"), pos.z)
                );
            }

            double yaw = toDouble(map.get("yaw"), currentState.yaw);
            double pitch = toDouble(map.get("pitch"), currentState.pitch);
            double roll = toDouble(map.get("roll"), 0);
            double fov = toDouble(map.get("fov"), 70);
            long dur = map.containsKey("duration") ? toLong(map.get("duration"), 1000L) : 1000L;

            cinematicKeyframes.add(new CinematicKeyframe(pos, yaw, pitch, roll, fov, dur));
        }

        if (cinematicKeyframes.isEmpty()) {
            LOGGER.warn("createCinematicFromList produced no valid keyframes");
            return;
        }

        currentKeyframeIndex = 0;
        keyframeStartTime = System.currentTimeMillis();
        isAnimating = false;
        useSmoothDamp = false;
        animationFollow = false;
    }

    @HostAccess.Export
    public void lookAt(Value target) {
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        if (target.isHostObject()) {
            Object obj = target.asHostObject();
            if (obj instanceof Entity) {
                lookAtEntity = (Entity) obj;
                lookAtPosition = null;
                return;
            } else if (obj instanceof Vector3) {
                Vector3 vec = (Vector3) obj;
                lookAtPosition = new Vector3d(vec.x, vec.y, vec.z);
                lookAtEntity = null;
                return;
            }
        }

        if (target.hasMember("x") && target.hasMember("y") && target.hasMember("z")) {
            lookAtPosition = new Vector3d(
                extractDouble(target, "x", 0),
                extractDouble(target, "y", 0),
                extractDouble(target, "z", 0)
            );
            lookAtEntity = null;
        }
    }

    @HostAccess.Export
    public void clearLookAt() {
        lookAtEntity = null;
        lookAtPosition = null;
    }

    private Vector3d resolveDollyLookTarget() {
        if (dollyTrackDirection) {
            Entity camEntity = client.getCameraEntity();
            double yaw = dollyUseCameraOrientation && camEntity != null ? camEntity.getYaw() : dollyDirectionYaw;
            double pitch = dollyUseCameraOrientation && camEntity != null ? camEntity.getPitch() : dollyDirectionPitch;
            Vector3d origin = camEntity != null
                    ? new Vector3d(camEntity.getX(), camEntity.getEyeY(), camEntity.getZ())
                    : new Vector3d(currentState.position);
            Vector3d forward = directionFromYawPitch(yaw, pitch);
            lookAtPosition = new Vector3d(
                    origin.x + forward.x * dollyDirectionDistance,
                    origin.y + forward.y * dollyDirectionDistance,
                    origin.z + forward.z * dollyDirectionDistance
            );
        }

        if (lookAtEntity != null && !lookAtEntity.isRemoved()) {
            return new Vector3d(lookAtEntity.getX(), lookAtEntity.getEyeY(), lookAtEntity.getZ());
        }
        if (lookAtPosition != null) {
            return new Vector3d(lookAtPosition);
        }
        return null;
    }

    @HostAccess.Export
    public void dollyZoom(double targetFOV, long duration) {
        DollyZoomConfig config = new DollyZoomConfig();
        config.targetFov = targetFOV;
        config.duration = duration;
        config.distance = 6.0;
        config.alignCamera = true;
        config.usePlayerOrientation = true;
        config.maintainTarget = true;
        startDollyZoom(config);
    }

    @HostAccess.Export
    public void dollyZoom(Value options) {
        startDollyZoom(parseDollyOptions(options));
    }

    public void dollyZoomFromMap(Map<String, Object> options) {
        startDollyZoom(parseDollyOptions(options));
    }

    private DollyZoomConfig parseDollyOptions(Value options) {
        DollyZoomConfig config = new DollyZoomConfig();
        if (options == null || !options.hasMembers()) {
            return config;
        }

        if (options.hasMember("targetFov")) config.targetFov = extractDouble(options, "targetFov", config.targetFov);
        if (options.hasMember("fov")) config.targetFov = extractDouble(options, "fov", config.targetFov);
        if (options.hasMember("duration")) config.duration = options.getMember("duration").asLong();
        if (options.hasMember("distance")) config.distance = extractDouble(options, "distance", config.distance);
        if (options.hasMember("alignCamera")) config.alignCamera = options.getMember("alignCamera").asBoolean();
        if (options.hasMember("maintainTarget")) config.maintainTarget = options.getMember("maintainTarget").asBoolean();

        if (options.hasMember("target")) {
            Value t = options.getMember("target");
            if (t.hasMember("x") && t.hasMember("y") && t.hasMember("z")) {
                config.targetPosition = new Vector3d(
                        extractDouble(t, "x", 0),
                        extractDouble(t, "y", 0),
                        extractDouble(t, "z", 0)
                );
            }
        }

        if (options.hasMember("direction")) {
            Value d = options.getMember("direction");
            if (d.hasMember("yaw")) config.directionYaw = extractDouble(d, "yaw", getPlayerYaw());
            if (d.hasMember("pitch")) config.directionPitch = extractDouble(d, "pitch", getPlayerPitch());
            if (d.hasMember("distance")) config.distance = extractDouble(d, "distance", config.distance);
            if (d.hasMember("fromPlayerLook")) config.usePlayerOrientation = d.getMember("fromPlayerLook").asBoolean();
        } else {
            if (options.hasMember("yaw")) config.directionYaw = extractDouble(options, "yaw", getPlayerYaw());
            if (options.hasMember("pitch")) config.directionPitch = extractDouble(options, "pitch", getPlayerPitch());
        }

        return config;
    }

    private DollyZoomConfig parseDollyOptions(Map<String, Object> options) {
        DollyZoomConfig config = new DollyZoomConfig();
        if (options == null || options.isEmpty()) {
            return config;
        }
        config.targetFov = toDouble(options.getOrDefault("targetFov", options.getOrDefault("fov", config.targetFov)), config.targetFov);
        config.duration = toLong(options.get("duration"), config.duration);
        config.distance = toDouble(options.get("distance"), config.distance);
        config.alignCamera = options.get("alignCamera") instanceof Boolean b ? b : config.alignCamera;
        config.maintainTarget = options.get("maintainTarget") instanceof Boolean b ? b : config.maintainTarget;

        Object targetObj = options.get("target");
        if (targetObj instanceof Map<?, ?> tMap) {
            double tx = toDouble(tMap.get("x"), Double.NaN);
            double ty = toDouble(tMap.get("y"), Double.NaN);
            double tz = toDouble(tMap.get("z"), Double.NaN);
            if (!Double.isNaN(tx) && !Double.isNaN(ty) && !Double.isNaN(tz)) {
                config.targetPosition = new Vector3d(tx, ty, tz);
            }
        }

        Object dirObj = options.get("direction");
        if (dirObj instanceof Map<?, ?> dMap) {
            config.directionYaw = toDouble(dMap.get("yaw"), getPlayerYaw());
            config.directionPitch = toDouble(dMap.get("pitch"), getPlayerPitch());
            config.distance = toDouble(dMap.get("distance"), config.distance);
            if (dMap.get("fromPlayerLook") instanceof Boolean b) {
                config.usePlayerOrientation = b;
            }
        } else {
            if (options.get("yaw") instanceof Number) config.directionYaw = toDouble(options.get("yaw"), getPlayerYaw());
            if (options.get("pitch") instanceof Number) config.directionPitch = toDouble(options.get("pitch"), getPlayerPitch());
        }
        return config;
    }

    private void startDollyZoom(DollyZoomConfig config) {
        if (!isCustomCameraActive()) {
            enableCustomCamera();
        }

        useSmoothDamp = false;
        Entity cameraEntity = client.getCameraEntity();

        dollyTrackDirection = config.maintainTarget;
        dollyUseCameraOrientation = config.usePlayerOrientation;
        dollyDirectionYaw = config.directionYaw != null ? config.directionYaw : (cameraEntity != null ? cameraEntity.getYaw() : currentState.yaw);
        dollyDirectionPitch = config.directionPitch != null ? config.directionPitch : (cameraEntity != null ? cameraEntity.getPitch() : currentState.pitch);
        dollyDirectionDistance = config.distance;

        if (config.targetPosition != null) {
            lookAtPosition = new Vector3d(config.targetPosition);
            lookAtEntity = null;
            dollyTrackDirection = false;
        } else if (config.usePlayerOrientation && cameraEntity != null) {
            lookAtEntity = null;
            Vector3d forward = directionFromYawPitch(cameraEntity.getYaw(), cameraEntity.getPitch());
            lookAtPosition = new Vector3d(
                    cameraEntity.getX() + forward.x * dollyDirectionDistance,
                    cameraEntity.getEyeY() + forward.y * dollyDirectionDistance,
                    cameraEntity.getZ() + forward.z * dollyDirectionDistance
            );
        } else {
            lookAtPosition = resolveDollyLookTarget();
        }

        if (config.alignCamera) {
            Vector3d forward = directionFromYawPitch(dollyDirectionYaw, dollyDirectionPitch);
            Vector3d target = lookAtEntity != null
                    ? new Vector3d(lookAtEntity.getX(), lookAtEntity.getEyeY(), lookAtEntity.getZ())
                    : (lookAtPosition != null ? new Vector3d(lookAtPosition) : resolveDollyLookTarget());
            if (target != null) {
                Vector3d camPos = new Vector3d(
                        target.x + forward.x * dollyDirectionDistance,
                        target.y + forward.y * dollyDirectionDistance,
                        target.z + forward.z * dollyDirectionDistance
                );
                currentState.position.set(camPos);
                targetState.position.set(camPos);
                currentState.yaw = dollyDirectionYaw;
                currentState.pitch = dollyDirectionPitch;
                targetState.yaw = dollyDirectionYaw;
                targetState.pitch = dollyDirectionPitch;
            }
        }

        Vector3d lookTarget = resolveDollyLookTarget();
        if (lookTarget == null) {
            LOGGER.warn("dollyZoom requires a lookAt target to be set");
            return;
        }

        double dx = lookTarget.x - currentState.position.x;
        double dy = lookTarget.y - currentState.position.y;
        double dz = lookTarget.z - currentState.position.z;
        dollyZoomStartDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        dollyZoomStartFov = currentState.fov;
        dollyZoomTargetFov = config.targetFov;
        dollyZoomDuration = config.duration;
        dollyZoomStartTime = System.currentTimeMillis();
        dollyZoomActive = true;
    }

    @HostAccess.Export
    public void stopPath() {
        pathPoints = null;
    }

    @HostAccess.Export
    public void stopCinematic() {
        cinematicKeyframes = null;
    }

    public void cleanUp() {
        this.jsContext = null;
        if (MoudClientMod.isCustomCameraActive()) {
            this.disableCustomCamera();
        }
        pathPoints = null;
        cinematicKeyframes = null;
        lookAtEntity = null;
        lookAtPosition = null;
        dollyZoomActive = false;
        LOGGER.info("CameraService cleaned up.");
    }
}
