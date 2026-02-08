package com.moud.server.editor.runtime;

import com.moud.network.MoudPackets;
import com.moud.server.editor.SceneDefaults;
import com.moud.server.instance.InstanceManager;
import com.moud.server.instance.SceneTerrainGenerator;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class TerrainRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerrainRuntimeAdapter.class);

    private final String sceneId;
    private final AtomicReference<SceneTerrainGenerator.TerrainSettings> currentSettings =
            new AtomicReference<>(SceneTerrainGenerator.defaultSettings());

    public TerrainRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public synchronized void create(MoudPackets.SceneObjectSnapshot snapshot) {
        applyTerrain(snapshot);
    }

    @Override
    public synchronized void update(MoudPackets.SceneObjectSnapshot snapshot) {
        applyTerrain(snapshot);
    }

    @Override
    public synchronized void remove() {
        SceneTerrainGenerator.TerrainSettings defaults = SceneTerrainGenerator.defaultSettings();
        currentSettings.set(defaults);
        InstanceManager.getInstance().updateDefaultTerrain(defaults);
    }

    private void applyTerrain(MoudPackets.SceneObjectSnapshot snapshot) {
        Map<String, Object> props = snapshot.properties();

        int requestedSize = intProperty(props.get("size"), SceneDefaults.BASE_SCENE_SIZE_BLOCKS);
        int requestedHeight = intProperty(props.get("terrainHeight"), SceneDefaults.BASE_TERRAIN_HEIGHT);
        Block surfaceBlock = blockProperty(props.get("surfaceBlock"), SceneDefaults.DEFAULT_SURFACE_BLOCK);
        Block fillBlock = blockProperty(props.get("fillBlock"), SceneDefaults.DEFAULT_FILL_BLOCK);

        SceneTerrainGenerator.TerrainSettings newSettings =
                SceneTerrainGenerator.clampSettings(requestedSize, requestedHeight, surfaceBlock, fillBlock);

        SceneTerrainGenerator.TerrainSettings previous = currentSettings.getAndSet(newSettings);
        if (newSettings.equals(previous)) {
            return;
        }

        InstanceManager.getInstance().updateDefaultTerrain(newSettings);
        LOGGER.info("Scene '{}' terrain settings applied for new chunks ({}x{} @ y={})",
                sceneId, newSettings.size(), newSettings.size(), newSettings.surfaceY());
    }

    private static int intProperty(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static Block blockProperty(Object raw, String fallbackId) {
        String namespace = raw != null ? raw.toString() : fallbackId;
        Block block = Block.fromNamespaceId(namespace);
        if (block == null) {
            block = Block.fromNamespaceId(fallbackId);
        }
        return block != null ? block : Block.AIR;
    }
}

