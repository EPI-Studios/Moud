package com.moud.client.camera;


import net.minecraft.client.render.Camera;

public class CameraManager {
    private static CameraAPI currentCamera;

    public static void setCamera(CameraAPI camera) {
        if (currentCamera != null && currentCamera.isEnabled()) {
            currentCamera.setEnabled(false);
        }
        currentCamera = camera;
    }

    public static CameraAPI getCurrentCamera() {
        return currentCamera;
    }

    public static void enableCamera() {
        if (currentCamera != null) {
            currentCamera.setEnabled(true);
        }
    }

    public static void disableCamera() {
        if (currentCamera != null) {
            currentCamera.setEnabled(false);
        }
    }

    public static boolean isCameraActive() {
        return currentCamera != null && currentCamera.isEnabled();
    }

    public static void tick(float partialTicks) {
        if (currentCamera != null && currentCamera.isEnabled()) {
            currentCamera.tick(partialTicks);
        }
    }

    public static void applyCameraTransform(Camera camera) {
        if (currentCamera != null && currentCamera.isEnabled()) {
            currentCamera.applyTransformation(camera);
        }
    }

    public static void handleInput(double deltaX, double deltaY, double deltaZ) {
        if (currentCamera != null && currentCamera.isEnabled()) {
            currentCamera.handleInput(deltaX, deltaY, deltaZ);
        }
    }
}