package com.moud.client.fabric.model.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moud.client.fabric.model.AnimationClip;
import com.moud.core.util.ParseUtils;
import com.moud.client.fabric.model.BoneNode;
import com.moud.client.fabric.model.BoneTrack;
import com.moud.client.fabric.model.CubeGeometry;
import com.moud.client.fabric.model.MeshGeometry;
import com.moud.client.fabric.model.ModelAsset;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.util.ClientDebugLog;
import fr.mrqsdf.bbmodelreader.BbModelReader;
import fr.mrqsdf.bbmodelreader.data.BbModel;
import fr.mrqsdf.bbmodelreader.data.Element;
import fr.mrqsdf.bbmodelreader.data.Face;
import fr.mrqsdf.bbmodelreader.data.Texture;
import fr.mrqsdf.bbmodelreader.data.animation.Animation;
import fr.mrqsdf.bbmodelreader.data.animation.Animator;
import fr.mrqsdf.bbmodelreader.data.animation.DataPoint;
import fr.mrqsdf.bbmodelreader.data.animation.Keyframe;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BbmodelLoader {
    private BbmodelLoader() {}

    public static ModelAsset load(byte[] jsonBytes, String modelName) {
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        String cacheKey = "moud_" + Long.toHexString(System.nanoTime());
        String sanitized = sanitizeOutliner(json);
        BbModel bb = BbModelReader.loadFromJson(sanitized, cacheKey);
        JsonObject root = asObject(JsonParser.parseString(json));
        if (root == null) {
            throw new IllegalStateException("bbmodel root is not a JSON object");
        }
        return fromBbModel(bb, root, modelName);
    }

    private static String sanitizeOutliner(String json) {
        try {
            JsonObject root = asObject(JsonParser.parseString(json));
            if (root == null || !root.has("outliner")) return json;
            JsonArray outliner = asArray(root.get("outliner"));
            if (outliner == null) return json;
            boolean needsFix = false;
            for (JsonElement el : outliner) {
                if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    needsFix = true;
                    break;
                }
            }
            if (!needsFix) return json;
            JsonArray children = new JsonArray();
            JsonArray fixedOutliner = new JsonArray();
            for (JsonElement el : outliner) {
                if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    children.add(el.getAsString());
                } else {
                    fixedOutliner.add(el);
                }
            }
            if (children.size() > 0) {
                JsonObject group = new JsonObject();
                group.addProperty("name", "__root__");
                group.addProperty("uuid", "00000000-0000-0000-0000-000000000000");
                group.add("origin", new JsonArray());
                group.addProperty("export", true);
                group.addProperty("isOpen", true);
                group.addProperty("locked", false);
                group.addProperty("visibility", true);
                group.addProperty("autouv", 0);
                group.add("children", children);
                fixedOutliner.add(group);
            }
            root.add("outliner", fixedOutliner);
            return root.toString();
        } catch (Exception e) {
            ClientDebugLog.warn("bbmodel", "sanitizeOutliner failed: " + e.getMessage());
            return json;
        }
    }

    private static ModelAsset fromBbModel(BbModel bb, JsonObject root, String modelName) {
        int resW = 64, resH = 64;
        if (bb.getResolution() != null) {
            resW = Math.max(1, bb.getResolution().getWidth());
            resH = Math.max(1, bb.getResolution().getHeight());
        }

        String safeName = modelName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");

        Texture[] textures = bb.getTextures();
        List<Identifier> texIds = new ArrayList<>();
        if (textures != null) {
            for (int i = 0; i < textures.length; i++) {
                Identifier id = Identifier.of("moud", "bbmodel/" + safeName + "/tex" + i);
                Texture tex = textures[i];
                if (tex != null) {
                    byte[] png = extractPng(tex);
                    if (png != null) MoudTextures.registerRaw(id, png);
                }
                texIds.add(id);
            }
        }
        Map<String, Integer> textureLookup = textureLookup(textures);

        Map<String, Element> elementsByUuid = new HashMap<>();
        Element[] elements = bb.getElements();
        if (elements != null) {
            for (Element e : elements) {
                if (e != null && e.getUuid() != null) elementsByUuid.put(e.getUuid(), e);
            }
        }
        Map<String, JsonObject> elementJsonByUuid = parseElementJson(root);

        List<BoneNode> rootBones = new ArrayList<>();
        Map<String, BoneNode> bonesByUuid = new HashMap<>();
        buildBones(root, elementsByUuid, elementJsonByUuid, textureLookup, rootBones, bonesByUuid);

        Map<String, AnimationClip> animations = new HashMap<>();
        Animation[] anims = bb.getAnimations();
        if (anims != null) {
            for (Animation anim : anims) {
                if (anim == null || anim.getName() == null) continue;
                animations.put(anim.getName(), buildClip(anim));
            }
        }

        return new ModelAsset(modelName, rootBones, bonesByUuid, animations, texIds, resW, resH);
    }

    private static void collectBones(BoneNode bone, Map<String, BoneNode> out) {
        if (!bone.uuid().isEmpty()) out.put(bone.uuid(), bone);
        for (BoneNode child : bone.children()) collectBones(child, out);
    }

    private static void buildBones(JsonObject root,
                                   Map<String, Element> elementsByUuid,
                                   Map<String, JsonObject> elementJsonByUuid,
                                   Map<String, Integer> textureLookup,
                                   List<BoneNode> rootBones,
                                   Map<String, BoneNode> bonesByUuid) {
        JsonArray outliner = root != null && root.has("outliner") && root.get("outliner").isJsonArray()
                ? asArray(root.get("outliner"))
                : null;
        if (outliner == null) {
            return;
        }
        int syntheticRootIndex = 0;
        for (JsonElement entry : outliner) {
            if (entry == null || entry.isJsonNull()) {
                continue;
            }
            BoneNode bone;
            JsonObject entryObject = asObject(entry);
            if (entryObject != null) {
                bone = buildBone(entryObject, elementsByUuid, elementJsonByUuid, textureLookup);
            } else if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                String uuid = entry.getAsString();
                bone = syntheticBoneForElement(uuid, elementsByUuid, elementJsonByUuid, textureLookup, syntheticRootIndex++);
            } else {
                bone = null;
            }
            if (bone == null) {
                continue;
            }
            rootBones.add(bone);
            collectBones(bone, bonesByUuid);
        }
    }

    private static BoneNode buildBone(JsonObject outliner,
                                      Map<String, Element> elements,
                                      Map<String, JsonObject> elementJsonByUuid,
                                      Map<String, Integer> textureLookup) {
        if (outliner == null) {
            return null;
        }
        float[] origin = jsonFloat3(outliner.get("origin"), 0f, 0f, 0f);
        float[] rotation = jsonFloat3(outliner.get("rotation"), 0f, 0f, 0f);
        float[] position = jsonFloat3(outliner.get("position"), 0f, 0f, 0f);

        List<CubeGeometry> cubes = new ArrayList<>();
        List<MeshGeometry> meshes = new ArrayList<>();
        List<BoneNode> children = new ArrayList<>();
        JsonArray childArray = outliner.has("children") && outliner.get("children").isJsonArray()
                ? asArray(outliner.get("children"))
                : null;
        if (childArray != null) {
            for (JsonElement child : childArray) {
                if (child == null || child.isJsonNull()) {
                    continue;
                }
                JsonObject childObject = asObject(child);
                if (childObject != null) {
                    BoneNode childBone = buildBone(childObject, elements, elementJsonByUuid, textureLookup);
                    if (childBone != null) {
                        children.add(childBone);
                    }
                } else if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String uuid2 = child.getAsString();
                    Element element = elements.get(uuid2);
                    if (element != null) {
                        CubeGeometry cube = toCube(element, elementJsonByUuid.get(uuid2), textureLookup);
                        if (cube != null) {
                            cubes.add(cube);
                        } else {
                            toMeshFaces(elementJsonByUuid.get(uuid2), meshes, textureLookup);
                        }
                    }
                }
            }
        }

        String uuid = string(outliner, "uuid", "");
        String name = string(outliner, "name", "");
        return new BoneNode(
                name, uuid,
                origin[0], origin[1], origin[2],
                position[0], position[1], position[2],
                rotation[0], rotation[1], rotation[2],
                cubes, meshes, children
        );
    }

    private static BoneNode syntheticBoneForElement(String uuid,
                                                    Map<String, Element> elements,
                                                    Map<String, JsonObject> elementJsonByUuid,
                                                    Map<String, Integer> textureLookup,
                                                    int index) {
        Element element = elements.get(uuid);
        if (element == null) {
            return null;
        }
        JsonObject raw = elementJsonByUuid.get(uuid);
        CubeGeometry cube = toCube(element, raw, textureLookup);
        if (cube != null) {
            return new BoneNode(
                    "__root_" + index, "",
                    0f, 0f, 0f,
                    0f, 0f, 0f,
                    0f, 0f, 0f,
                    List.of(cube), List.of(), List.of()
            );
        }
        List<MeshGeometry> meshes = new ArrayList<>();
        toMeshFaces(raw, meshes, textureLookup);
        if (meshes.isEmpty()) {
            return null;
        }
        return new BoneNode(
                "__root_" + index, "",
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f,
                List.of(), meshes, List.of()
        );
    }

    private static CubeGeometry toCube(Element e, JsonObject raw, Map<String, Integer> textureLookup) {
        float[] from = e.getFrom(), to = e.getTo();
        if (from == null || to == null || from.length < 3 || to.length < 3) return null;
        Face f = e.getFaces();
        float[] origin = rawFloat3(raw, "origin", 0.0f, 0.0f, 0.0f);
        float[] rotation = rawFloat3(raw, "rotation", 0.0f, 0.0f, 0.0f);
        float inflate = rawFloat(raw, "inflate", 0.0f);
        return new CubeGeometry(
                from[0], from[1], from[2], to[0], to[1], to[2],
                origin[0], origin[1], origin[2],
                rotation[0], rotation[1], rotation[2],
                inflate,
                toFaceUv(f != null ? f.getNorth() : null, rawFace(raw, "north"), textureLookup),
                toFaceUv(f != null ? f.getSouth() : null, rawFace(raw, "south"), textureLookup),
                toFaceUv(f != null ? f.getEast()  : null, rawFace(raw, "east"), textureLookup),
                toFaceUv(f != null ? f.getWest()  : null, rawFace(raw, "west"), textureLookup),
                toFaceUv(f != null ? f.getUp()    : null, rawFace(raw, "up"), textureLookup),
                toFaceUv(f != null ? f.getDown()  : null, rawFace(raw, "down"), textureLookup)
        );
    }

    private static void toMeshFaces(JsonObject raw, List<MeshGeometry> out, Map<String, Integer> textureLookup) {
        if (raw == null) return;
        JsonObject verticesJson = asObject(raw.get("vertices"));
        JsonObject facesJson = asObject(raw.get("faces"));
        if (verticesJson == null || facesJson == null) return;

        Map<String, float[]> verts = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : verticesJson.entrySet()) {
            JsonArray arr = asArray(e.getValue());
            if (arr != null && arr.size() >= 3) {
                verts.put(e.getKey(), new float[]{
                        arr.get(0).getAsFloat(),
                        arr.get(1).getAsFloat(),
                        arr.get(2).getAsFloat()
                });
            }
        }

        float[] origin = rawFloat3(raw, "origin", 0f, 0f, 0f);
        float[] rotation = rawFloat3(raw, "rotation", 0f, 0f, 0f);
        boolean hasRot = rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0;

        for (Map.Entry<String, JsonElement> e : facesJson.entrySet()) {
            JsonObject face = asObject(e.getValue());
            if (face == null) continue;
            JsonArray vertOrder = asArray(face.get("vertices"));
            if (vertOrder == null || vertOrder.size() < 4) continue;
            JsonObject uvMap = asObject(face.get("uv"));
            int texIdx = rawTextureIndex(face, 0, textureLookup);

            String[] names = new String[4];
            for (int i = 0; i < 4; i++) {
                JsonElement ve = vertOrder.get(i);
                names[i] = ve != null && ve.isJsonPrimitive() ? ve.getAsString() : null;
            }

            float[] pos = new float[12];
            float[] uvs = new float[8];
            boolean valid = true;
            for (int i = 0; i < 4; i++) {
                if (names[i] == null) { valid = false; break; }
                float[] p = verts.get(names[i]);
                if (p == null) { valid = false; break; }
                float px = p[0], py = p[1], pz = p[2];
                if (hasRot) {

                    px -= origin[0]; py -= origin[1]; pz -= origin[2];
                    float rx = (float) Math.toRadians(rotation[0]);
                    float ry = (float) Math.toRadians(rotation[1]);
                    float rz = (float) Math.toRadians(rotation[2]);
                    float cx = (float) Math.cos(rx), sx2 = (float) Math.sin(rx);
                    float cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
                    float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);

                    float tx = cz * px - sz * py;
                    float ty = sz * px + cz * py;
                    float tz = pz;
                    float tx2 = cy * tx + sy * tz;
                    float tz2 = -sy * tx + cy * tz;
                    float ty2 = ty;
                    float tx3 = tx2;
                    float ty3 = cx * ty2 - sx2 * tz2;
                    float tz3 = sx2 * ty2 + cx * tz2;
                    px = tx3 + origin[0];
                    py = ty3 + origin[1];
                    pz = tz3 + origin[2];
                }
                pos[i * 3]     = px;
                pos[i * 3 + 1] = py;
                pos[i * 3 + 2] = pz;
                if (uvMap != null) {
                    JsonArray uv = asArray(uvMap.get(names[i]));
                    if (uv != null && uv.size() >= 2) {
                        uvs[i * 2]     = uv.get(0).getAsFloat();
                        uvs[i * 2 + 1] = uv.get(1).getAsFloat();
                    }
                }
            }
            if (!valid) continue;

            out.add(new MeshGeometry(
                    pos[0], pos[1], pos[2],
                    pos[3], pos[4], pos[5],
                    pos[6], pos[7], pos[8],
                    pos[9], pos[10], pos[11],
                    uvs[0], uvs[1],
                    uvs[2], uvs[3],
                    uvs[4], uvs[5],
                    uvs[6], uvs[7],
                    texIdx
            ));
        }
    }

    private static int rawInt(JsonObject object, String key, int fallback) {
        if (object == null || key == null || !object.has(key)) return fallback;
        JsonElement el = object.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()
                ? el.getAsInt()
                : fallback;
    }

    private static CubeGeometry.FaceUV toFaceUv(Face.FaceValue fv, JsonObject rawFace, Map<String, Integer> textureLookup) {
        if (fv == null) return null;
        float[] uv = fv.getUv();
        if (uv == null || uv.length < 4) return null;
        int fallbackTex = fv.getTexture() != null ? fv.getTexture() : 0;
        int tex = rawTextureIndex(rawFace, fallbackTex, textureLookup);
        int rot = fv.getRotation() != null ? fv.getRotation() : 0;
        return new CubeGeometry.FaceUV(uv[0], uv[1], uv[2], uv[3], rot, tex);
    }

    private static JsonObject rawFace(JsonObject element, String faceName) {
        JsonObject faces = element == null ? null : asObject(element.get("faces"));
        return faces == null ? null : asObject(faces.get(faceName));
    }

    private static int rawTextureIndex(JsonObject object, int fallback, Map<String, Integer> lookup) {
        if (object == null || !object.has("texture")) {
            return fallback;
        }
        JsonElement el = object.get("texture");
        if (el == null || !el.isJsonPrimitive()) {
            return fallback;
        }
        var prim = el.getAsJsonPrimitive();
        if (prim.isNumber()) {
            return prim.getAsInt();
        }
        if (!prim.isString()) {
            return fallback;
        }
        return lookupTextureIndex(prim.getAsString(), fallback, lookup);
    }

    private static int lookupTextureIndex(String ref, int fallback, Map<String, Integer> lookup) {
        if (ref == null) {
            return fallback;
        }
        String key = ref.trim();
        if (key.isEmpty()) {
            return fallback;
        }
        while (key.startsWith("#")) {
            key = key.substring(1);
        }
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
        }
        Integer direct = lookup == null ? null : lookup.get(key);
        if (direct != null) {
            return direct;
        }
        Integer lower = lookup == null ? null : lookup.get(key.toLowerCase(Locale.ROOT));
        return lower != null ? lower : fallback;
    }

    private static AnimationClip buildClip(Animation anim) {
        float duration = anim.getLength() != null ? anim.getLength() : 0f;
        String loop    = anim.getLoop()   != null ? anim.getLoop()   : "once";

        Map<String, BoneTrack> tracks = new HashMap<>();
        Map<String, Animator> animators = anim.getAnimators();
        if (animators != null) {
            for (Map.Entry<String, Animator> entry : animators.entrySet()) {
                Animator animator = entry.getValue();
                if (animator == null || animator.getKeyframes() == null) continue;
                tracks.put(entry.getKey(), buildTrack(animator.getKeyframes()));
            }
        }
        return new AnimationClip(anim.getName(), duration, loop, tracks);
    }

    private static BoneTrack buildTrack(Keyframe[] keyframes) {
        List<float[]> pos = new ArrayList<>(), rot = new ArrayList<>(), scale = new ArrayList<>();
        for (Keyframe kf : keyframes) {
            if (kf == null || kf.getTime() == null || kf.getDataPoints() == null || kf.getDataPoints().isEmpty()) continue;
            DataPoint dp = kf.getDataPoints().get(0);
            if (dp == null) continue;
            float t = kf.getTime();
            float x = ParseUtils.parseFloat(dp.getX(), 0f), y = ParseUtils.parseFloat(dp.getY(), 0f), z = ParseUtils.parseFloat(dp.getZ(), 0f);
            switch (kf.getChannel() != null ? kf.getChannel() : "") {
                case "position" -> pos.add(new float[]{t, x, y, z});
                case "rotation" -> rot.add(new float[]{t, x, y, z});
                case "scale"    -> scale.add(new float[]{t, x, y, z});
            }
        }
        pos.sort(Comparator.comparingDouble(a -> a[0]));
        rot.sort(Comparator.comparingDouble(a -> a[0]));
        scale.sort(Comparator.comparingDouble(a -> a[0]));
        return new BoneTrack(
                times(pos), vals(pos, 1), vals(pos, 2), vals(pos, 3),
                times(rot), vals(rot, 1), vals(rot, 2), vals(rot, 3),
                times(scale), vals(scale, 1), vals(scale, 2), vals(scale, 3)
        );
    }

    private static float[] times(List<float[]> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i)[0];
        return a;
    }

    private static float[] vals(List<float[]> list, int comp) {
        float[] a = new float[list.size()];
        for (int i = 0; i < list.size(); i++) a[i] = list.get(i)[comp];
        return a;
    }

    private static Map<String, Integer> textureLookup(Texture[] textures) {
        Map<String, Integer> out = new HashMap<>();
        if (textures == null) {
            return out;
        }
        for (int i = 0; i < textures.length; i++) {
            Texture texture = textures[i];
            addTextureKey(out, Integer.toString(i), i);
            addTextureKey(out, "#" + i, i);
            if (texture == null) {
                continue;
            }
            addTextureKey(out, texture.getUuid(), i);
            addTextureKey(out, texture.getId(), i);
            addTextureKey(out, texture.getName(), i);
            addTextureKey(out, texture.getPath(), i);
            addTextureKey(out, texture.getRelative_path(), i);
        }
        return out;
    }

    private static void addTextureKey(Map<String, Integer> out, String key, int index) {
        if (out == null || key == null) {
            return;
        }
        String normalized = key.trim();
        if (normalized.isEmpty()) {
            return;
        }
        out.putIfAbsent(normalized, index);
        out.putIfAbsent(normalized.toLowerCase(Locale.ROOT), index);
        if (!normalized.startsWith("#")) {
            out.putIfAbsent("#" + normalized, index);
            out.putIfAbsent(("#" + normalized).toLowerCase(Locale.ROOT), index);
        }
    }

    private static byte[] extractPng(Texture tex) {
        String source = tex.getSource();
        if (source == null || !source.contains(",")) return null;
        try {
            return Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, JsonObject> parseElementJson(JsonObject root) {
        Map<String, JsonObject> out = new HashMap<>();
        JsonArray elements = root != null && root.has("elements") && root.get("elements").isJsonArray()
                ? asArray(root.get("elements"))
                : null;
        if (elements == null) {
            return out;
        }
        for (JsonElement element : elements) {
            JsonObject object = asObject(element);
            if (object == null) {
                continue;
            }
            String uuid = string(object, "uuid", "");
            if (!uuid.isEmpty()) {
                out.put(uuid, object);
            }
        }
        return out;
    }

    private static float[] rawFloat3(JsonObject object, String key, float x, float y, float z) {
        if (object == null || key == null || !object.has(key)) {
            return new float[]{x, y, z};
        }
        return jsonFloat3(object.get(key), x, y, z);
    }

    private static float rawFloat(JsonObject object, String key, float fallback) {
        if (object == null || key == null || !object.has(key)) {
            return fallback;
        }
        JsonElement el = object.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()
                ? el.getAsFloat()
                : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || key == null || !object.has(key)) {
            return fallback;
        }
        JsonElement el = object.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : fallback;
    }

    private static float[] jsonFloat3(JsonElement element, float x, float y, float z) {
        float[] out = new float[]{x, y, z};
        JsonArray arr = asArray(element);
        if (arr == null) {
            return out;
        }
        if (arr.size() > 0 && arr.get(0).isJsonPrimitive()) out[0] = arr.get(0).getAsFloat();
        if (arr.size() > 1 && arr.get(1).isJsonPrimitive()) out[1] = arr.get(1).getAsFloat();
        if (arr.size() > 2 && arr.get(2).isJsonPrimitive()) out[2] = arr.get(2).getAsFloat();
        return out;
    }

    private static JsonObject asObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static JsonArray asArray(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.isJsonArray() ? element.getAsJsonArray() : null;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }
}
