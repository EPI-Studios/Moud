package com.moud.client.editor.scene.blueprint;

import java.util.HashMap;
import java.util.Map;

public final class BlueprintObject {
    public String type;
    public String label;
    public float[] position = new float[3];
    public float[] rotation = new float[3];
    public float[] scale = new float[]{1f, 1f, 1f};
    public String modelPath;
    public String texture;
    public float[] boundsMin;
    public float[] boundsMax;
    public Map<String, Object> properties = new HashMap<>();
    public BlueprintObject() {}
}
