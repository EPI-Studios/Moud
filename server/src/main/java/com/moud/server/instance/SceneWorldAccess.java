package com.moud.server.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.server.editor.SceneManager;
import com.moud.server.logging.MoudLogger;
import net.hollowcube.polar.PolarWorldAccess;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SceneWorldAccess implements PolarWorldAccess {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(SceneWorldAccess.class);

    private static final int MAGIC = 0x4D4F5544; // "MOUD"
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_DEPTH = 32;

    private static final byte TYPE_NULL = 0;
    private static final byte TYPE_BOOL = 1;
    private static final byte TYPE_NUMBER = 2;
    private static final byte TYPE_STRING = 3;
    private static final byte TYPE_MAP = 4;
    private static final byte TYPE_LIST = 5;

    private static final ObjectMapper LEGACY_JSON = new ObjectMapper();

    private final String sceneId;
    private final Path projectRoot;

    SceneWorldAccess(String sceneId, Path projectRoot) {
        this.sceneId = Objects.requireNonNullElse(sceneId, "");
        this.projectRoot = projectRoot;
    }

    @Override
    public void loadWorldData(@NotNull Instance instance, @NotNull NetworkBuffer userData) {
        try {
            int magic = userData.read(NetworkBuffer.INT);
            if (magic != MAGIC) {
                return;
            }
            int version = userData.read(NetworkBuffer.VAR_INT);
            if (version != FORMAT_VERSION) {
                return;
            }
            SceneManager.PersistedScene persisted = readScene(userData);
            SceneManager.getInstance().loadPersistedScene(persisted);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode scene data from world userData; falling back to defaults", e);
        }
    }

    @Override
    public void saveWorldData(@NotNull Instance instance, @NotNull NetworkBuffer userData) {
        SceneManager sceneManager = SceneManager.getInstance();
        SceneManager.PersistedScene persisted = sceneManager.exportPersistedScene(sceneId);
        userData.write(NetworkBuffer.INT, MAGIC);
        userData.write(NetworkBuffer.VAR_INT, FORMAT_VERSION);
        writeScene(userData, persisted);
    }

    void ensureSceneInitialized(@NotNull Instance instance) {
        SceneManager sceneManager = SceneManager.getInstance();
        if (sceneManager.hasScene(sceneId)) {
            return;
        }

        SceneManager.PersistedScene migrated = tryLoadLegacyJson();
        if (migrated != null) {
            sceneManager.loadPersistedScene(migrated);
            return;
        }

        sceneManager.loadPersistedScene(sceneManager.baseSceneTemplate(sceneId));
    }

    private SceneManager.PersistedScene tryLoadLegacyJson() {
        if (projectRoot == null || sceneId.isEmpty()) {
            return null;
        }
        Path legacyPath = projectRoot.resolve(".moud").resolve("scenes").resolve(sceneId + ".json");
        if (!Files.exists(legacyPath)) {
            return null;
        }
        try {
            return LEGACY_JSON.readValue(legacyPath.toFile(), SceneManager.PersistedScene.class);
        } catch (IOException e) {
            LOGGER.warn("Failed to migrate legacy scene JSON {}", legacyPath, e);
            return null;
        }
    }

    private static void writeScene(NetworkBuffer out, SceneManager.PersistedScene scene) {
        out.write(NetworkBuffer.STRING, Objects.requireNonNullElse(scene.getSceneId(), ""));
        out.write(NetworkBuffer.LONG, scene.getVersion());
        List<SceneManager.PersistedSceneObject> objects = scene.getObjects();
        if (objects == null) {
            out.write(NetworkBuffer.VAR_INT, 0);
            return;
        }
        out.write(NetworkBuffer.VAR_INT, objects.size());
        for (SceneManager.PersistedSceneObject obj : objects) {
            out.write(NetworkBuffer.STRING, Objects.requireNonNullElse(obj.getObjectId(), ""));
            out.write(NetworkBuffer.STRING, Objects.requireNonNullElse(obj.getObjectType(), ""));
            writeValue(out, obj.getProperties(), 0);
        }
    }

    private static SceneManager.PersistedScene readScene(NetworkBuffer in) {
        String sceneId = in.read(NetworkBuffer.STRING);
        long version = in.read(NetworkBuffer.LONG);
        int objectCount = in.read(NetworkBuffer.VAR_INT);
        List<SceneManager.PersistedSceneObject> objects = new ArrayList<>(Math.max(0, objectCount));
        for (int i = 0; i < objectCount; i++) {
            String objectId = in.read(NetworkBuffer.STRING);
            String objectType = in.read(NetworkBuffer.STRING);
            Object propsRaw = readValue(in, 0);
            Map<String, Object> props = propsRaw instanceof Map<?, ?> map ? castMap(map) : Map.of();
            objects.add(new SceneManager.PersistedSceneObject(objectId, objectType, props));
        }
        return new SceneManager.PersistedScene(sceneId, version, objects);
    }

    private static void writeValue(NetworkBuffer out, Object value, int depth) {
        if (depth >= MAX_DEPTH) {
            out.write(NetworkBuffer.BYTE, TYPE_STRING);
            out.write(NetworkBuffer.STRING, "");
            return;
        }

        if (value == null) {
            out.write(NetworkBuffer.BYTE, TYPE_NULL);
            return;
        }

        if (value instanceof Boolean bool) {
            out.write(NetworkBuffer.BYTE, TYPE_BOOL);
            out.write(NetworkBuffer.BOOLEAN, bool);
            return;
        }

        if (value instanceof Number number) {
            out.write(NetworkBuffer.BYTE, TYPE_NUMBER);
            out.write(NetworkBuffer.DOUBLE, number.doubleValue());
            return;
        }

        if (value instanceof String str) {
            out.write(NetworkBuffer.BYTE, TYPE_STRING);
            out.write(NetworkBuffer.STRING, str);
            return;
        }

        if (value instanceof Map<?, ?> map) {
            out.write(NetworkBuffer.BYTE, TYPE_MAP);
            Map<String, Object> cast = castMap(map);
            out.write(NetworkBuffer.VAR_INT, cast.size());
            cast.forEach((key, v) -> {
                out.write(NetworkBuffer.STRING, key);
                writeValue(out, v, depth + 1);
            });
            return;
        }

        if (value instanceof List<?> list) {
            out.write(NetworkBuffer.BYTE, TYPE_LIST);
            out.write(NetworkBuffer.VAR_INT, list.size());
            for (Object v : list) {
                writeValue(out, v, depth + 1);
            }
            return;
        }

        out.write(NetworkBuffer.BYTE, TYPE_STRING);
        out.write(NetworkBuffer.STRING, String.valueOf(value));
    }

    private static Object readValue(NetworkBuffer in, int depth) {
        if (depth >= MAX_DEPTH) {
            return null;
        }
        byte type = in.read(NetworkBuffer.BYTE);
        return switch (type) {
            case TYPE_NULL -> null;
            case TYPE_BOOL -> in.read(NetworkBuffer.BOOLEAN);
            case TYPE_NUMBER -> in.read(NetworkBuffer.DOUBLE);
            case TYPE_STRING -> in.read(NetworkBuffer.STRING);
            case TYPE_MAP -> {
                int size = in.read(NetworkBuffer.VAR_INT);
                Map<String, Object> map = new HashMap<>(Math.max(0, size));
                for (int i = 0; i < size; i++) {
                    String key = in.read(NetworkBuffer.STRING);
                    map.put(key, readValue(in, depth + 1));
                }
                yield map;
            }
            case TYPE_LIST -> {
                int size = in.read(NetworkBuffer.VAR_INT);
                List<Object> list = new ArrayList<>(Math.max(0, size));
                for (int i = 0; i < size; i++) {
                    list.add(readValue(in, depth + 1));
                }
                yield list;
            }
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> map = new HashMap<>();
        raw.forEach((k, v) -> {
            if (k != null) {
                map.put(String.valueOf(k), v);
            }
        });
        return map;
    }
}
