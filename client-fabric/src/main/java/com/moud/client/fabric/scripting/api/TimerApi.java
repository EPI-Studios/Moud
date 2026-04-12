package com.moud.client.fabric.scripting.api;

import java.util.HashMap;
import java.util.Map;

public final class RenderApi {

    private final Map<Long, Map<String, Object>> uniformOverrides = new HashMap<>();

    private final Map<Long, Map<String, Object>> materialParamOverrides = new HashMap<>();

    public RenderApi() {
    }


    public void setUniform(long nodeId, String key, double value) {
        uniformOverrides.computeIfAbsent(nodeId, k -> new HashMap<>()).put(key, value);
    }

    public void setUniformVec(long nodeId, String key, double x, double y, double z, double w) {
        uniformOverrides.computeIfAbsent(nodeId, k -> new HashMap<>())
                .put(key, new double[]{x, y, z, w});
    }


    public void setMaterialParam(long nodeId, String paramName, Object value) {
        materialParamOverrides.computeIfAbsent(nodeId, k -> new HashMap<>()).put(paramName, value);
    }


    public void setTint(long nodeId, double r, double g, double b, double a) {
        materialParamOverrides.computeIfAbsent(nodeId, k -> new HashMap<>())
                .put("tint", new double[]{r, g, b, a});
    }

    public void setVisible(long nodeId, boolean visible) {
        materialParamOverrides.computeIfAbsent(nodeId, k -> new HashMap<>())
                .put("visible", visible);
    }


    public void clearFrameOverrides() {
        uniformOverrides.clear();
        materialParamOverrides.clear();
    }


    public Map<Long, Map<String, Object>> uniformOverrides() {
        return uniformOverrides;
    }

    public Map<Long, Map<String, Object>> materialParamOverrides() {
        return materialParamOverrides;
    }
}