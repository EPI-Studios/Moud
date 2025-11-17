package com.moud.server.physics.mesh;

import com.moud.api.collision.OBB;
import com.moud.server.assets.AssetManager;
import com.moud.server.collision.MeshBoxDecomposer;
import com.moud.server.editor.SceneManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelCollisionLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelCollisionLibrary.class);
    private static final Map<String, float[]> VERTEX_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<OBB>> COLLISION_BOX_CACHE = new ConcurrentHashMap<>();

    private ModelCollisionLibrary() {
    }

    public static float[] getVertices(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        return VERTEX_CACHE.computeIfAbsent(modelPath, ModelCollisionLibrary::loadVertices);
    }

    private static float[] loadVertices(String modelPath) {
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
            float[] positions = ObjModelLoader.loadPositions(inputStream);
            if (positions.length < 9) {
                LOGGER.warn("Model '{}' does not contain enough vertices for collision mesh", modelPath);
                return null;
            }
            return positions;
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
        return resourcePath.replace('\\', '/');
    }

    public static List<OBB> getCollisionBoxes(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        return COLLISION_BOX_CACHE.computeIfAbsent(modelPath, ModelCollisionLibrary::generateCollisionBoxes);
    }

    private static List<OBB> generateCollisionBoxes(String modelPath) {
        float[] vertices = getVertices(modelPath);
        if (vertices == null || vertices.length < 9) {
            LOGGER.warn("Cannot generate collision boxes for '{}': no vertex data", modelPath);
            return null;
        }

        List<OBB> boxes = MeshBoxDecomposer.decompose(vertices, 3);
        LOGGER.info("Generated {} collision boxes for model '{}'", boxes.size(), modelPath);
        return boxes;
    }

    public static void clearCache() {
        VERTEX_CACHE.clear();
        COLLISION_BOX_CACHE.clear();
    }
}
