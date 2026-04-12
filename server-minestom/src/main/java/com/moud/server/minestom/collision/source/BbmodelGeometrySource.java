package com.moud.server.minestom.collision.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moud.core.assets.AssetMeta;
import com.moud.core.assets.ResPath;
import com.moud.core.scene.Node;
import com.moud.core.physics.CollisionGeometry;
import com.moud.server.minestom.assets.AssetStore;
import com.moud.server.minestom.collision.SceneNodeTransform;
import com.moud.server.minestom.project.ProjectService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BbmodelGeometrySource implements CollisionGeometrySource {
    private static final int[] BOX_INDICES = {
            0, 1, 2, 0, 2, 3,
            4, 6, 5, 4, 7, 6,
            0, 4, 5, 0, 5, 1,
            1, 5, 6, 1, 6, 2,
            2, 6, 7, 2, 7, 3,
            3, 7, 4, 3, 4, 0
    };

    private final ProjectService project;
    private final AssetStore assets;
    private final Map<String, CachedGeometry> cache = new ConcurrentHashMap<>();

    public BbmodelGeometrySource(ProjectService project, AssetStore assets) {
        this.project = project;
        this.assets = assets;
    }

    @Override
    public boolean supports(String typeId) {
        return "Model3D".equals(typeId);
    }

    @Override
    public CollisionGeometry extract(Node node, String typeId) {
        if (node == null || !"Model3D".equals(typeId)) {
            return CollisionGeometry.EMPTY;
        }
        String modelPath = node.getProperty("model_path");
        if (modelPath == null || modelPath.isBlank()) {
            return CollisionGeometry.EMPTY;
        }
        try {
            ModelBytes model = loadModelBytes(modelPath);
            if (model == null || model.bytes().length == 0) {
                return CollisionGeometry.EMPTY;
            }
            String key = model.cacheKey();
            CachedGeometry cached = cache.get(key);
            if (cached != null) {
                return cached.geometry;
            }
            CollisionGeometry geometry = loadGeometry(model.bytes());
            cache.put(key, new CachedGeometry(geometry));
            return geometry;
        } catch (Exception ignored) {
            return CollisionGeometry.EMPTY;
        }
    }

    private ModelBytes loadModelBytes(String modelPath) throws Exception {
        try {
            ResPath resPath = new ResPath(modelPath);
            if (assets != null) {
                AssetMeta meta = assets.meta(resPath);
                if (meta != null && meta.hash() != null && assets.hasBlob(meta.hash())) {
                    byte[] bytes = assets.readBlob(meta.hash());
                    return new ModelBytes(bytes, resPath.value() + "#" + meta.hash().hex());
                }
            }
        } catch (Exception ignored) {
        }

        Path file = project.resolveProjectPath(modelPath);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        long modified = Files.getLastModifiedTime(file).toMillis();
        byte[] bytes = Files.readAllBytes(file);
        return new ModelBytes(bytes, file.toAbsolutePath().normalize() + "#" + modified);
    }

    private static CollisionGeometry loadGeometry(byte[] bytes) throws Exception {
        JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, JsonObject> elements = parseElements(root);
        GeometryBuilder builder = new GeometryBuilder();
        JsonArray outliner = asArray(root.get("outliner"));
        SceneNodeTransform.Transform rootTransform = new SceneNodeTransform.Transform(
                new SceneNodeTransform.Vec3(0, 0, 0),
                SceneNodeTransform.Quat.IDENTITY,
                new SceneNodeTransform.Vec3(1.0 / 16.0, 1.0 / 16.0, 1.0 / 16.0)
        );
        if (outliner != null && !outliner.isEmpty()) {
            for (JsonElement entry : outliner) {
                appendOutlinerEntry(entry, rootTransform, elements, builder);
            }
        } else {
            for (JsonObject element : elements.values()) {
                appendCube(element, rootTransform, builder);
            }
        }
        return builder.build();
    }

    private static void appendOutlinerEntry(JsonElement entry,
                                            SceneNodeTransform.Transform parent,
                                            Map<String, JsonObject> elements,
                                            GeometryBuilder builder) {
        if (entry == null || entry.isJsonNull()) {
            return;
        }
        JsonObject group = asObject(entry);
        if (group != null) {
            SceneNodeTransform.Vec3 pivot = vec3(group.get("origin"), 0.0, 0.0, 0.0);
            SceneNodeTransform.Vec3 pos = vec3(group.get("position"), 0.0, 0.0, 0.0);
            SceneNodeTransform.Quat rot = quat(group.get("rotation"));
            // T(pos + pivot) * R * T(-pivot)
            SceneNodeTransform.Transform bone = parent.compose(new SceneNodeTransform.Transform(
                    pivot.add(pos).sub(rot.rotate(pivot)),
                    rot,
                    new SceneNodeTransform.Vec3(1.0, 1.0, 1.0)
            ));
            JsonArray children = asArray(group.get("children"));
            if (children == null) {
                return;
            }
            for (JsonElement child : children) {
                appendOutlinerEntry(child, bone, elements, builder);
            }
            return;
        }
        if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
            return;
        }
        JsonObject cube = elements.get(entry.getAsString());
        if (cube != null) {
            appendCube(cube, parent, builder);
        }
    }

    private static void appendCube(JsonObject cube, SceneNodeTransform.Transform parent, GeometryBuilder builder) {
        if (cube == null || !"cube".equals(string(cube, "type", "cube"))) {
            return;
        }
        SceneNodeTransform.Vec3 from = vec3(cube.get("from"), 0.0, 0.0, 0.0);
        SceneNodeTransform.Vec3 to = vec3(cube.get("to"), 0.0, 0.0, 0.0);
        double inflate = number(cube.get("inflate"), 0.0);
        SceneNodeTransform.Vec3 origin = vec3(cube.get("origin"), 0.0, 0.0, 0.0);
        SceneNodeTransform.Quat rotation = quat(cube.get("rotation"));

        double x0 = from.x() - inflate;
        double y0 = from.y() - inflate;
        double z0 = from.z() - inflate;
        double sx = Math.max(1e-6, (to.x() - from.x()) + inflate * 2.0);
        double sy = Math.max(1e-6, (to.y() - from.y()) + inflate * 2.0);
        double sz = Math.max(1e-6, (to.z() - from.z()) + inflate * 2.0);

        // Cube transform: T(origin) * R * T(-origin) * T(x0, y0, z0) * S(sx, sy, sz)
        // = T(origin + rot.rotate(new Vec3(x0, y0, z0).sub(origin))) * R * S(sx, sy, sz)
        SceneNodeTransform.Transform cubeTransform = parent.compose(new SceneNodeTransform.Transform(
                origin.add(rotation.rotate(new SceneNodeTransform.Vec3(x0, y0, z0).sub(origin))),
                rotation,
                new SceneNodeTransform.Vec3(sx, sy, sz)
        ));
        builder.appendUnitBox(cubeTransform);
    }

    private static Map<String, JsonObject> parseElements(JsonObject root) {
        HashMap<String, JsonObject> out = new HashMap<>();
        JsonArray array = asArray(root.get("elements"));
        if (array == null) {
            return out;
        }
        for (JsonElement element : array) {
            JsonObject object = asObject(element);
            if (object == null) {
                continue;
            }
            String uuid = string(object, "uuid", "");
            if (!uuid.isBlank()) {
                out.put(uuid, object);
            }
        }
        return out;
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || key == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double number(JsonElement element, double fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            double value = element.getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static SceneNodeTransform.Vec3 vec3(JsonElement element, double x, double y, double z) {
        JsonArray array = asArray(element);
        if (array == null) {
            return new SceneNodeTransform.Vec3(x, y, z);
        }
        return new SceneNodeTransform.Vec3(
                array.size() > 0 ? number(array.get(0), x) : x,
                array.size() > 1 ? number(array.get(1), y) : y,
                array.size() > 2 ? number(array.get(2), z) : z
        );
    }

    private static SceneNodeTransform.Quat quat(JsonElement element) {
        SceneNodeTransform.Vec3 rot = vec3(element, 0.0, 0.0, 0.0);
        return SceneNodeTransform.Quat.fromEulerDegZYX(rot.x(), rot.y(), rot.z());
    }

    private record CachedGeometry(CollisionGeometry geometry) {
    }

    private record ModelBytes(byte[] bytes, String cacheKey) {
    }

    private static final class GeometryBuilder {
        private final ArrayList<Float> vertices = new ArrayList<>();
        private final ArrayList<Integer> indices = new ArrayList<>();

        void appendUnitBox(SceneNodeTransform.Transform transform) {
            int base = vertices.size() / 3;
            appendVertex(transform, 0.0, 0.0, 0.0);
            appendVertex(transform, 1.0, 0.0, 0.0);
            appendVertex(transform, 1.0, 1.0, 0.0);
            appendVertex(transform, 0.0, 1.0, 0.0);
            appendVertex(transform, 0.0, 0.0, 1.0);
            appendVertex(transform, 1.0, 0.0, 1.0);
            appendVertex(transform, 1.0, 1.0, 1.0);
            appendVertex(transform, 0.0, 1.0, 1.0);
            for (int index : BOX_INDICES) {
                indices.add(base + index);
            }
        }

        private void appendVertex(SceneNodeTransform.Transform transform, double x, double y, double z) {
            SceneNodeTransform.Vec3 v = transform.apply(x, y, z);
            vertices.add((float) v.x());
            vertices.add((float) v.y());
            vertices.add((float) v.z());
        }

        CollisionGeometry build() {
            if (vertices.isEmpty() || indices.isEmpty()) {
                return CollisionGeometry.EMPTY;
            }
            float[] vertexArray = new float[vertices.size()];
            for (int i = 0; i < vertices.size(); i++) {
                vertexArray[i] = vertices.get(i);
            }
            int[] indexArray = new int[indices.size()];
            for (int i = 0; i < indices.size(); i++) {
                indexArray[i] = indices.get(i);
            }
            return new CollisionGeometry(vertexArray, indexArray);
        }
    }
}
