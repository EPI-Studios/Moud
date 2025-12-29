package com.moud.server.physics.mesh;

import com.moud.api.collision.OBB;
import com.moud.api.util.PathUtils;
import com.moud.server.assets.AssetManager;
import com.moud.server.collision.MeshBoxDecomposer;
import com.moud.server.editor.SceneManager;
import com.github.stephengold.joltjni.vhacd.ConvexHull;
import com.github.stephengold.joltjni.vhacd.Decomposer;
import com.github.stephengold.joltjni.vhacd.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class ModelCollisionLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelCollisionLibrary.class);
    private static final Map<String, MeshData> MESH_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<OBB>> COLLISION_BOX_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<float[]>> VHACD_HULL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CompletableFuture<List<OBB>>> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    private static final int MAX_MESH_CACHE = 64;
    private static final int MAX_COLLISION_CACHE = 128;
    private static final int MAX_HULL_CACHE = 64;

    private ModelCollisionLibrary() {
    }

    public static float[] getVertices(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        MeshData mesh = getMesh(modelPath);
        return mesh != null ? mesh.vertices() : null;
    }

    public static MeshData getMesh(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        MeshData cached = MESH_CACHE.get(modelPath);
        if (cached != null) {
            return cached;
        }
        MeshData loaded = loadMesh(modelPath);
        if (loaded != null) {
            MESH_CACHE.put(modelPath, loaded);
            trimCache(MESH_CACHE, MAX_MESH_CACHE);
        } else {
            MESH_CACHE.remove(modelPath);
        }
        return loaded;
    }

    private static MeshData loadMesh(String modelPath) {
        AssetManager assetManager = SceneManager.getInstance().getAssetManager();
        if (assetManager == null) {
            LOGGER.warn("Cannot resolve model '{}' because AssetManager is not initialized", modelPath);
            return null;
        }

        String assetId = toAssetId(modelPath);
        if (assetId == null) {
            LOGGER.warn("Unsupported model path '{}'", modelPath);
            return null;
        }

        AssetManager.LoadedAsset loadedAsset;
        try {
            loadedAsset = assetManager.loadAsset(assetId);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Failed to load model asset '{}': {}", assetId, e.getMessage());
            return null;
        }

        if (!(loadedAsset instanceof AssetManager.ModelAsset modelAsset)) {
            LOGGER.warn("Asset '{}' is not a model file", assetId);
            return null;
        }

        try (InputStream inputStream = new ByteArrayInputStream(modelAsset.getData())) {
            MeshData mesh = ObjModelLoader.loadMesh(inputStream);
            if (mesh.vertices().length < 9 || mesh.indices().length < 3) {
                LOGGER.warn("Model '{}' does not contain enough data for collision mesh", modelPath);
                return null;
            }
            return mesh;
        } catch (IOException e) {
            LOGGER.warn("Failed to parse OBJ model '{}': {}", modelPath, e.getMessage());
            return null;
        }
    }

    private static String toAssetId(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        if (resourcePath.contains(":")) {
            String[] parts = resourcePath.split(":", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                return parts[0] + "/" + parts[1];
            }
            return null;
        }
        return PathUtils.normalizeSlashes(resourcePath);
    }

    public static List<OBB> getCollisionBoxes(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        List<OBB> boxes = COLLISION_BOX_CACHE.compute(modelPath, (k, existing) -> {
            if (existing != null && !existing.isEmpty() && isReasonable(existing)) {
                return existing;
            }
            List<OBB> generated = generateCollisionBoxes(modelPath);
            if (generated == null) {
                COLLISION_BOX_CACHE.remove(modelPath);
            }
            return generated;
        });
        trimCache(COLLISION_BOX_CACHE, MAX_COLLISION_CACHE);
        return boxes;
    }

    public static CompletableFuture<List<OBB>> getCollisionBoxesAsync(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<OBB> cached = COLLISION_BOX_CACHE.get(modelPath);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return PENDING.computeIfAbsent(modelPath, key ->
                CompletableFuture.supplyAsync(() -> {
                    List<OBB> boxes = generateCollisionBoxes(key);
                    if (boxes != null) {
                        COLLISION_BOX_CACHE.put(key, boxes);
                        trimCache(COLLISION_BOX_CACHE, MAX_COLLISION_CACHE);
                    }
                    return boxes;
                }, WORKERS).whenComplete((boxes, throwable) -> PENDING.remove(key)));
    }

    public static List<float[]> getConvexHulls(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return List.of();
        }
        List<float[]> hulls = VHACD_HULL_CACHE.computeIfAbsent(modelPath, key -> {
            MeshData mesh = getMesh(key);
            if (mesh == null || mesh.vertices().length < 9 || mesh.indices().length < 3) {
                LOGGER.warn("Cannot generate VHACD hulls for '{}': no mesh data", modelPath);
                return List.of();
            }
            try {
                Parameters params = new Parameters()
                        .setResolution(80_000)
                        .setMaxConvexHulls(16)
                        .setMaxRecursionDepth(10)
                        .setFindBestPlane(true)
                        .setShrinkWrap(true);
                Decomposer decomposer = new Decomposer();
                var decomposed = decomposer.decompose(mesh.vertices(), mesh.indices(), params);
                if (decomposed == null || decomposed.isEmpty()) {
                    LOGGER.warn("VHACD produced no hulls for '{}'", modelPath);
                    return List.of();
                }
                List<float[]> result = decomposed.stream()
                        .map(ModelCollisionLibrary::toPointArray)
                        .filter(arr -> arr != null && arr.length >= 9)
                        .collect(Collectors.toList());
                LOGGER.info("Generated {} VHACD hulls for model '{}'", result.size(), modelPath);
                return result;
            } catch (Exception e) {
                LOGGER.warn("VHACD failed for model '{}': {}", modelPath, e.getMessage());
                return List.of();
            }
        });
        trimCache(VHACD_HULL_CACHE, MAX_HULL_CACHE);
        return hulls;
    }

    private static float[] toPointArray(ConvexHull hull) {
        try {
            var buffer = hull.getPointsAsBuffer();
            if (buffer == null) {
                return null;
            }
            float[] arr = new float[buffer.remaining()];
            buffer.get(arr);
            return arr;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<OBB> generateCollisionBoxes(String modelPath) {
        MeshData mesh = getMesh(modelPath);
        if (mesh == null || mesh.vertices().length < 9 || mesh.indices().length < 3) {
            LOGGER.warn("Cannot generate collision boxes for '{}': no mesh data", modelPath);
            return null;
        }

        List<OBB> boxes = MeshBoxDecomposer.decompose(mesh.vertices(), mesh.indices(), modelPath);
        LOGGER.info("Generated {} collision boxes for model '{}'", boxes.size(), modelPath);
        return boxes;
    }

    public static double[] computeBounds(String modelPath) {
        MeshData mesh = getMesh(modelPath);
        float[] vertices = mesh != null ? mesh.vertices() : null;
        if (vertices == null || vertices.length < 3) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static boolean isReasonable(List<OBB> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            return false;
        }
        double maxExtent = 0;
        for (OBB obb : boxes) {
            if (obb == null || obb.halfExtents == null) continue;
            maxExtent = Math.max(maxExtent, Math.max(Math.max(obb.halfExtents.x, obb.halfExtents.y), obb.halfExtents.z) * 2.0);
            if (maxExtent > 256.0) {
                return false;
            }
        }
        return true;
    }


    private static <K, V> void trimCache(Map<K, V> cache, int maxSize) {
        int oversize = cache.size() - maxSize;
        if (oversize <= 0) {
            return;
        }
        var iterator = cache.keySet().iterator();
        while (oversize-- > 0 && iterator.hasNext()) {
            cache.remove(iterator.next());
        }
    }

    public record MeshData(float[] vertices, int[] indices) {}
}
