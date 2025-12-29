package com.moud.client.editor.scene.blueprint;

import java.util.HashMap;
import java.util.Map;

public final class BlueprintMarker {
    public String name;
    public float[] position = new float[3];
    public float[] rotation = new float[3];
    public float[] scale = new float[]{1f, 1f, 1f};
    public Map<String, Object> properties = new HashMap<>();
    public BlueprintMarker() {}
}
