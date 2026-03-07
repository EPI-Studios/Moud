package com.moud.client.fabric.render.material;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;


public final class MoudMaterialParser {
    private static final Gson GSON = new GsonBuilder().create();

    private MoudMaterialParser() {
    }

    public static MoudMaterial parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(text);
            if (el == null || !el.isJsonObject()) {
                return null;
            }
            root = el.getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }

        JsonElement shaderEl = root.get("shader");
        if (shaderEl == null || !shaderEl.isJsonPrimitive()) {
            return null;
        }
        String shader = shaderEl.getAsString();
        if (shader == null || shader.isBlank()) {
            return null;
        }

        HashMap<String, MoudMaterial.Param> params = new HashMap<>();
        JsonElement paramsEl = root.get("params");
        if (paramsEl != null && paramsEl.isJsonObject()) {
            parseParams(paramsEl.getAsJsonObject(), params);
        }

        JsonElement uniformsEl = root.get("uniforms");
        if (uniformsEl != null && uniformsEl.isJsonObject()) {
            parseParams(uniformsEl.getAsJsonObject(), params);
        }

        return new MoudMaterial(shader.trim(), Map.copyOf(params));
    }

    private static void parseParams(JsonObject obj, HashMap<String, MoudMaterial.Param> out) {
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (e == null || e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            String key = e.getKey();
            JsonElement val = e.getValue();

            if (val.isJsonNull()) {
                continue;
            }
            if (val.isJsonPrimitive()) {
                var prim = val.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    out.put(key, new MoudMaterial.Param.Number(prim.getAsFloat()));
                } else if (prim.isBoolean()) {
                    out.put(key, new MoudMaterial.Param.Bool(prim.getAsBoolean()));
                } else if (prim.isString()) {
                    out.put(key, new MoudMaterial.Param.Texture(prim.getAsString()));
                }
                continue;
            }
            if (val.isJsonArray()) {
                float[] vec = parseFloatArray(val.getAsJsonArray());
                if (vec != null) {
                    out.put(key, new MoudMaterial.Param.Vec(vec));
                }
            }
        }
    }

    private static float[] parseFloatArray(JsonArray arr) {
        if (arr == null) {
            return null;
        }
        int n = arr.size();
        if (n < 1 || n > 4) {
            return null;
        }
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            JsonElement el = arr.get(i);
            if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            out[i] = el.getAsFloat();
        }
        return out;
    }
}
