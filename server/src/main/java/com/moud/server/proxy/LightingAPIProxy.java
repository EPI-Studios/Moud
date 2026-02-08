package com.moud.server.proxy;

import com.moud.server.ts.TsExpose;
import com.moud.api.math.Vector3;
import com.moud.server.lighting.ServerLightingManager;
import org.graalvm.polyglot.HostAccess;

import java.util.HashMap;
import java.util.Map;

@TsExpose
public class LightingAPIProxy {

    private final ServerLightingManager manager;

    public LightingAPIProxy() {
        this.manager = ServerLightingManager.getInstance();
    }

    @HostAccess.Export
    public void createPointLight(long lightId, Vector3 position, Vector3 color, double radius, double brightness) {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "point");
        props.put("x", position.x);
        props.put("y", position.y);
        props.put("z", position.z);
        props.put("r", color.x);
        props.put("g", color.y);
        props.put("b", color.z);
        props.put("radius", radius);
        props.put("brightness", brightness);
        manager.createOrUpdateLight(lightId, props);
    }

    @HostAccess.Export
    public void createAreaLight(long lightId, Vector3 position, Vector3 direction, Vector3 color, double width, double height, double brightness) {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "area");
        props.put("x", position.x);
        props.put("y", position.y);
        props.put("z", position.z);
        props.put("dirX", direction.x);
        props.put("dirY", direction.y);
        props.put("dirZ", direction.z);
        props.put("r", color.x);
        props.put("g", color.y);
        props.put("b", color.z);
        props.put("width", width);
        props.put("height", height);
        props.put("brightness", brightness);
        props.put("angle", 45.0);
        props.put("distance", 10.0);
        manager.createOrUpdateLight(lightId, props);
    }
    @HostAccess.Export
    public void updateLight(long lightId, Map<String, Object> properties) {
        manager.createOrUpdateLight(lightId, properties);
    }

    @HostAccess.Export
    public void removeLight(long lightId) {
        manager.removeLight(lightId);
    }
}