package com.moud.client.api.service;

import com.moud.client.camera.CameraAPI;
import com.moud.client.camera.CameraManager;
import com.moud.client.camera.impl.ScriptableCameraImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CameraService {
    private final MinecraftClient client;
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraService.class);
    private Context jsContext;
    public CameraService() {
        this.client = MinecraftClient.getInstance();
        initializeScriptableCamera();
    }

    private void initializeScriptableCamera() {
        ScriptableCameraImpl camera = new ScriptableCameraImpl();
        CameraManager.setCamera(camera);
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        LOGGER.debug("NetworkService received new GraalVM Context.");
    }

    public void enableCustomCamera() {
        Entity player = client.player;
        if (player != null) {
            CameraAPI camera = CameraManager.getCurrentCamera();
            if (camera != null) {
                camera.setPosition(player.getX(), player.getEyeY(), player.getZ());
                camera.setYaw(player.getYaw());
                camera.setPitch(player.getPitch());
                camera.trackEntity(player);
                camera.prevPosition = camera.getPosition();
                camera.targetPosition = camera.getPosition();
            }
        }
        CameraManager.enableCamera();

    }

    public void disableCustomCamera() {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null) {
            camera.stopTracking();
        }
        CameraManager.disableCamera();

    }

    public boolean isCustomCameraActive() {
        return CameraManager.isCameraActive();
    }

    public float getPitch() {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            return camera.getPitch();
        }
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getPitch() : 0.0f;
    }

    public void setPitch(float pitch) {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            camera.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
        } else {
            Entity cameraEntity = client.getCameraEntity();
            if (cameraEntity != null) {
                cameraEntity.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
            }
        }
    }

    public float getYaw() {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            return camera.getYaw();
        }
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getYaw() : 0.0f;
    }

    public void setYaw(float yaw) {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            camera.setYaw(yaw);
        } else {
            Entity cameraEntity = client.getCameraEntity();
            if (cameraEntity != null) {
                cameraEntity.setYaw(yaw);
            }
        }
    }

    public double getX() {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            return camera.getPosition().x;
        }
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getX() : 0.0;
    }

    public double getY() {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            return camera.getPosition().y;
        }
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getY() : 0.0;
    }

    public double getZ() {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            return camera.getPosition().z;
        }
        Entity cameraEntity = client.getCameraEntity();
        return cameraEntity != null ? cameraEntity.getZ() : 0.0;
    }

    public void setPosition(double x, double y, double z) {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            camera.setPosition(x, y, z);
        } else {
            Entity cameraEntity = client.getCameraEntity();
            if (cameraEntity != null) {
                cameraEntity.setPosition(x, y, z);
            }
        }
    }

    public void addRotation(float pitchDelta, float yawDelta) {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            float newPitch = MathHelper.clamp(camera.getPitch() + pitchDelta, -90.0f, 90.0f);
            float newYaw = camera.getYaw() + yawDelta;
            camera.setPitch(newPitch);
            camera.setYaw(newYaw);
        } else {
            Entity cameraEntity = client.getCameraEntity();
            if (cameraEntity != null) {
                float newPitch = MathHelper.clamp(cameraEntity.getPitch() + pitchDelta, -90.0f, 90.0f);
                float newYaw = cameraEntity.getYaw() + yawDelta;
                cameraEntity.setPitch(newPitch);
                cameraEntity.setYaw(newYaw);
            }
        }
    }

    public float getFov() {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            return camera.getFOV();
        }
        return (float) client.options.getFov().getValue();
    }

    public void setFov(double fov) {
        CameraAPI camera = CameraManager.getCurrentCamera();
        if (camera != null && camera.isEnabled()) {
            camera.setFOV((float) fov);
        } else {
            client.options.getFov().setValue((int) fov);
        }
    }

    public boolean isThirdPerson() {
        return !client.options.getPerspective().isFirstPerson();
    }

    public void setThirdPerson(boolean thirdPerson) {
        if (thirdPerson && client.options.getPerspective().isFirstPerson()) {
            client.options.setPerspective(client.options.getPerspective().next());
        } else if (!thirdPerson && !client.options.getPerspective().isFirstPerson()) {
            client.options.setPerspective(client.options.getPerspective().next());
        }
    }

    public void lookAt(double targetX, double targetY, double targetZ) {
        double currentX = getX();
        double currentY = getY();
        double currentZ = getZ();

        double deltaX = targetX - currentX;
        double deltaY = targetY - currentY;
        double deltaZ = targetZ - currentZ;

        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(-deltaX, deltaZ) * 180.0 / Math.PI);
        float pitch = (float) (Math.atan2(-deltaY, distance) * 180.0 / Math.PI);

        setYaw(yaw);
        setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    public void cleanUp() {

        jsContext = null;
        LOGGER.info("CameraService cleaned up.");
    }
}