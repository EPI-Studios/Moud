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
            if (val.isJsonObject()) {
                MoudMaterial.Param p = parseTypedParam(val.getAsJsonObject());
                if (p != null) {
                    out.put(key, p);
                }
                continue;
            }
            if (val.isJsonPrimitive()) {
                var prim = val.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    out.put(key, new MoudMaterial.Param.Number(prim.getAsFloat()));
                } else if (prim.isBoolean()) {
                    out.put(key, new MoudMaterial.Param.Bool(prim.getAsBoolean()));
                } else if (prim.isString()) {
                    String s = prim.getAsString();
                    if (key.endsWith("_texture") || key.endsWith("_map") || key.equals("orm_texture")) {
                        out.put(key, new MoudMaterial.Param.Texture(s));
                    } else {
                        out.put(key, new MoudMaterial.Param.StringParam(s));
                    }
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

    private static MoudMaterial.Param parseTypedParam(JsonObject obj) {
        if (obj == null || obj.isEmpty()) {
            return null;
        }

        JsonElement typeEl = obj.get("type");
        JsonElement valueEl = obj.get("value");
        if (typeEl != null && typeEl.isJsonPrimitive() && typeEl.getAsJsonPrimitive().isString() && valueEl != null) {
            String type = typeEl.getAsString() == null ? "" : typeEl.getAsString().trim().toLowerCase();
            return switch (type) {
                case "texture" -> valueEl.isJsonPrimitive() && valueEl.getAsJsonPrimitive().isString()
                        ? new MoudMaterial.Param.Texture(valueEl.getAsString())
                        : null;
                case "string" -> valueEl.isJsonPrimitive() && valueEl.getAsJsonPrimitive().isString()
                        ? new MoudMaterial.Param.StringParam(valueEl.getAsString())
                        : null;
                case "bool" -> valueEl.isJsonPrimitive() && valueEl.getAsJsonPrimitive().isBoolean()
                        ? new MoudMaterial.Param.Bool(valueEl.getAsBoolean())
                        : null;
                case "int" -> valueEl.isJsonPrimitive() && valueEl.getAsJsonPrimitive().isNumber()
                        ? new MoudMaterial.Param.Number(Math.round(valueEl.getAsFloat()))
                        : null;
                case "float", "number" -> valueEl.isJsonPrimitive() && valueEl.getAsJsonPrimitive().isNumber()
                        ? new MoudMaterial.Param.Number(valueEl.getAsFloat())
                        : null;
                case "vec2", "vec3", "vec4", "vec" -> valueEl.isJsonArray()
                        ? vecParam(valueEl.getAsJsonArray())
                        : null;
                default -> null;
            };
        }

        JsonElement tex = obj.get("texture");
        if (tex != null && tex.isJsonPrimitive() && tex.getAsJsonPrimitive().isString()) {
            return new MoudMaterial.Param.Texture(tex.getAsString());
        }
        JsonElement str = obj.get("string");
        if (str != null && str.isJsonPrimitive() && str.getAsJsonPrimitive().isString()) {
            return new MoudMaterial.Param.StringParam(str.getAsString());
        }
        JsonElement bool = obj.get("bool");
        if (bool != null && bool.isJsonPrimitive() && bool.getAsJsonPrimitive().isBoolean()) {
            return new MoudMaterial.Param.Bool(bool.getAsBoolean());
        }
        JsonElement integer = obj.get("int");
        if (integer != null && integer.isJsonPrimitive() && integer.getAsJsonPrimitive().isNumber()) {
            return new MoudMaterial.Param.Number(Math.round(integer.getAsFloat()));
        }
        JsonElement flt = obj.get("float");
        if (flt != null && flt.isJsonPrimitive() && flt.getAsJsonPrimitive().isNumber()) {
            return new MoudMaterial.Param.Number(flt.getAsFloat());
        }
        JsonElement num = obj.get("number");
        if (num != null && num.isJsonPrimitive() && num.getAsJsonPrimitive().isNumber()) {
            return new MoudMaterial.Param.Number(num.getAsFloat());
        }

        JsonElement v2 = obj.get("vec2");
        if (v2 != null && v2.isJsonArray()) {
            return vecParam(v2.getAsJsonArray());
        }
        JsonElement v3 = obj.get("vec3");
        if (v3 != null && v3.isJsonArray()) {
            return vecParam(v3.getAsJsonArray());
        }
        JsonElement v4 = obj.get("vec4");
        if (v4 != null && v4.isJsonArray()) {
            return vecParam(v4.getAsJsonArray());
        }
        JsonElement vec = obj.get("vec");
        if (vec != null && vec.isJsonArray()) {
            return vecParam(vec.getAsJsonArray());
        }

        return null;
    }

    private static MoudMaterial.Param vecParam(JsonArray arr) {
        float[] vec = parseFloatArray(arr);
        return vec != null ? new MoudMaterial.Param.Vec(vec) : null;
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
