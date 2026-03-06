package com.moud.client.fabric.render.material;


import java.util.Map;


public final class MoudMaterialWriter {
    private static final Gson GSON_PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private MoudMaterialWriter() {
    }

    public static String toJson(MoudMaterial material) {
        if (material == null) {
            return "";
        }

        JsonObject root = new JsonObject();
        root.addProperty("shader", material.shader());

        JsonObject params = new JsonObject();
        for (Map.Entry<String, MoudMaterial.Param> e : material.params().entrySet()) {
            if (e == null || e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            String key = e.getKey();
            MoudMaterial.Param p = e.getValue();
            if (p instanceof MoudMaterial.Param.Number num) {
                params.addProperty(key, num.value());
            } else if (p instanceof MoudMaterial.Param.Bool b) {
                params.addProperty(key, b.value());
            } else if (p instanceof MoudMaterial.Param.Texture t) {
                params.addProperty(key, t.textureRef());
            } else if (p instanceof MoudMaterial.Param.StringParam s) {
                params.addProperty(key, s.value());
            } else if (p instanceof MoudMaterial.Param.Vec vec) {
                float[] values = vec.values();
                if (values == null || values.length == 0) {
                    continue;
                }
                JsonArray arr = new JsonArray();
                for (float v : values) {
                    arr.add(v);
                }
                params.add(key, arr);
            }
        }
        root.add("params", params);

        return GSON_PRETTY.toJson(root);
    }
}

