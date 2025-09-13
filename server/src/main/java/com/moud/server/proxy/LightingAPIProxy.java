
package com.moud.server.proxy;

import com.moud.server.lighting.ServerLightingManager;

public class LightingAPIProxy {

    private final ServerLightingManager manager;

    public LightingAPIProxy() {
        this.manager = ServerLightingManager.getInstance();
    }

    public long createPointLight(double x, double y, double z, double radius, double r, double g, double b, double brightness) {
        return manager.createPointLight(x, y, z, radius, r, g, b, brightness);
    }

    public long createAreaLight(double x, double y, double z, double width, double height, double r, double g, double b, double brightness) {
        return manager.createAreaLight(x, y, z, width, height, r, g, b, brightness);
    }

    public void updateLightPosition(long lightId, double x, double y, double z) {
        manager.updateLightPosition(lightId, x, y, z);
    }

    public void updateLightColor(long lightId, double r, double g, double b) {
        manager.updateLightColor(lightId, r, g, b);
    }

    public void updateLightBrightness(long lightId, double brightness) {
        manager.updateLightBrightness(lightId, brightness);
    }

    public void removeLight(long lightId) {
        manager.removeLight(lightId);
    }
}