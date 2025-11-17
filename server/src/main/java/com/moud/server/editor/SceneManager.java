package com.moud.server.editor;

import com.moud.network.MoudPackets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.moud.server.editor.runtime.SceneRuntimeAdapter;
import com.moud.server.editor.runtime.SceneRuntimeFactory;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class SceneManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            SceneManager.class,
            LogContext.builder().put("subsystem", "scene-editor").build()
    );

    private static final SceneManager INSTANCE = new SceneManager();

    private final ConcurrentMap<String, SceneState> scenes = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private Path storageDirectory;
    private Path projectRoot;
    private com.moud.server.assets.AssetManager assetManager;

    private SceneManager() {}

    public static SceneManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(Path projectRoot) {
        if (storageDirectory != null) {
            return;
        }
        this.projectRoot = projectRoot;
        storageDirectory = projectRoot.resolve(".moud").resolve("scenes");
        try {
            Files.createDirectories(storageDirectory);
            loadScenesFromDisk();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize scene storage", e);
        }
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public void setAssetManager(com.moud.server.assets.AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public com.moud.server.assets.AssetManager getAssetManager() {
        return assetManager;
    }

    public SceneSnapshot createSnapshot(String sceneId) {
        SceneState state = scenes.computeIfAbsent(sceneId, id -> new SceneState(id));
        var objects = state.objects.values().stream()
                .map(SceneManager::toSnapshot)
                .collect(Collectors.toCollection(ArrayList::new));
        return new SceneSnapshot(state.version.get(), Collections.unmodifiableList(objects));
    }

    public List<MoudPackets.EditorAssetDefinition> getEditorAssets() {
        if (assetManager == null) {
            return List.of();
        }
        List<MoudPackets.EditorAssetDefinition> assets = new ArrayList<>();

        assetManager.getDiscoveredAssets().values().stream()
                .filter(meta -> meta.getType() == com.moud.server.assets.AssetDiscovery.AssetType.MODEL)
                .forEach(meta -> {
                    String label = humanize(meta.getId());
                    String assetPath = toResourcePath(meta.getId());
                    Map<String, Object> defaults = Map.of(
                            "modelPath", assetPath,
                            "label", label
                    );
                    assets.add(new MoudPackets.EditorAssetDefinition(meta.getId(), label, "model", defaults));
                });

        assetManager.getDiscoveredAssets().values().stream()
                .filter(meta -> meta.getType() == com.moud.server.assets.AssetDiscovery.AssetType.TEXTURE)
                .forEach(meta -> {
                    String label = "Display: " + humanize(meta.getId());
                    String assetPath = toResourcePath(meta.getId());
                    Map<String, Object> defaults = Map.of(
                            "displayContent", assetPath,
                            "label", label,
                            "displayType", "image"
                    );
                    assets.add(new MoudPackets.EditorAssetDefinition("display/" + meta.getId(), label, "display", defaults));
                });

        assets.addAll(builtInLightAssets());

        return assets;
    }

    private String humanize(String id) {
        String fileName = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) fileName = fileName.substring(0, dot);
        return fileName.replace('_', ' ').replace('-', ' ');
    }

    private String toResourcePath(String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            return "moud:";
        }
        int slash = assetId.indexOf('/');
        if (slash > 0 && slash < assetId.length() - 1) {
            String namespace = assetId.substring(0, slash);
            String path = assetId.substring(slash + 1);
            return namespace + ":" + path;
        }
        return "moud:" + assetId;
    }

    private List<MoudPackets.EditorAssetDefinition> builtInLightAssets() {
        List<MoudPackets.EditorAssetDefinition> lights = new ArrayList<>();

        Map<String, Object> warmPoint = new HashMap<>();
        warmPoint.put("lightType", "point");
        warmPoint.put("radius", 6.0);
        warmPoint.put("brightness", 1.2);
        warmPoint.put("color", colorMap(1.0, 0.93, 0.8));
        lights.add(new MoudPackets.EditorAssetDefinition("light/point_warm", "Point Light", "light", warmPoint));

        Map<String, Object> softArea = new HashMap<>();
        softArea.put("lightType", "area");
        softArea.put("width", 4.0);
        softArea.put("height", 2.5);
        softArea.put("distance", 10.0);
        softArea.put("angle", 45.0);
        softArea.put("brightness", 1.5);
        softArea.put("color", colorMap(1.0, 0.97, 0.9));
        softArea.put("direction", vectorMap(0.0, -1.0, 0.0));
        lights.add(new MoudPackets.EditorAssetDefinition("light/area_soft", "Area Light", "light", softArea));

        return lights;
    }

    private static Map<String, Object> colorMap(double r, double g, double b) {
        Map<String, Object> color = new HashMap<>();
        color.put("r", r);
        color.put("g", g);
        color.put("b", b);
        return color;
    }

    private static Map<String, Object> vectorMap(double x, double y, double z) {
        Map<String, Object> map = new HashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        return map;
    }

    public SceneEditResult applyEdit(String sceneId, String action, Map<String, Object> payload, long clientVersion) {
        SceneState state = scenes.computeIfAbsent(sceneId, id -> new SceneState(id));
        String normalizedAction = action == null ? "" : action.toLowerCase();

        switch (normalizedAction) {
            case "create":
                return createObject(state, payload);
            case "update":
                return updateObject(state, payload);
            case "delete":
                return deleteObject(state, payload);
            default:
                return SceneEditResult.failure("Unknown scene edit action: " + action, state.version.get());
        }
    }

    private SceneEditResult createObject(SceneState state, Map<String, Object> payload) {
        String objectId = asString(payload.getOrDefault("id", ""));
        if (objectId.isEmpty()) {
            objectId = "obj-" + System.currentTimeMillis();
        }
        String objectType = asString(payload.getOrDefault("type", "generic"));
        Map<String, Object> properties = snapshotProperties(payload.getOrDefault("properties", Map.of()));

        SceneObject object = new SceneObject(objectId, objectType, properties);
        state.objects.put(objectId, object);

        try {
            initializeAdapter(state.sceneId, object);
            long version = state.version.incrementAndGet();
            LOGGER.debug("Scene object {} created (type={})", objectId, objectType);
            persistScene(state.sceneId);
            return SceneEditResult.success(objectId, toSnapshot(object), "created", version);
        } catch (Exception e) {
            LOGGER.error("Failed to create runtime object for {}", objectId, e);
            state.objects.remove(objectId);
            return SceneEditResult.failure("Failed to spawn runtime object: " + e.getMessage(), state.version.get());
        }
    }

    private SceneEditResult updateObject(SceneState state, Map<String, Object> payload) {
        String objectId = asString(payload.get("id"));
        if (objectId.isEmpty()) {
            return SceneEditResult.failure("Missing object id for update", state.version.get());
        }
        SceneObject existing = state.objects.get(objectId);
        if (existing == null) {
            return SceneEditResult.failure("Object not found: " + objectId, state.version.get());
        }
        Map<String, Object> properties = snapshotProperties(payload.getOrDefault("properties", Map.of()));
        existing.properties.clear();
        existing.properties.putAll(properties);
        try {
            initializeAdapter(state.sceneId, existing);
            long version = state.version.incrementAndGet();
            LOGGER.debug("Scene object {} updated", objectId);
            persistScene(state.sceneId);
            return SceneEditResult.success(objectId, toSnapshot(existing), "updated", version);
        } catch (Exception e) {
            LOGGER.error("Failed to update runtime object for {}", objectId, e);
            return SceneEditResult.failure("Failed to update runtime object: " + e.getMessage(), state.version.get());
        }
    }

    private SceneEditResult deleteObject(SceneState state, Map<String, Object> payload) {
        String objectId = asString(payload.get("id"));
        if (objectId.isEmpty()) {
            return SceneEditResult.failure("Missing object id for delete", state.version.get());
        }
        SceneObject removed = state.objects.remove(objectId);
        if (removed == null) {
            return SceneEditResult.failure("Object not found: " + objectId, state.version.get());
        }
        if (removed.adapter != null) {
            removed.adapter.remove();
        }
        long version = state.version.incrementAndGet();
        LOGGER.debug("Scene object {} deleted", objectId);
        persistScene(state.sceneId);
        return SceneEditResult.success(objectId, null, "deleted", version);
    }

    private static String asString(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshotProperties(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new ConcurrentHashMap<>();
        }
        Map<String, Object> copy = new ConcurrentHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                copy.put(String.valueOf(key), value);
            }
        });
        return copy;
    }

    private void initializeAdapter(String sceneId, SceneObject object) throws Exception {
        if (object.adapter == null) {
            SceneRuntimeAdapter adapter = SceneRuntimeFactory.create(sceneId, object.type);
            object.adapter = adapter;
            if (adapter != null) {
                adapter.create(toSnapshot(object));
            }
        } else if (object.adapter != null) {
            object.adapter.update(toSnapshot(object));
        }
    }

    private static MoudPackets.SceneObjectSnapshot toSnapshot(SceneObject object) {
        return new MoudPackets.SceneObjectSnapshot(object.id, object.type, new ConcurrentHashMap<>(object.properties));
    }

    private static final class SceneState {
        private final String sceneId;
        private final AtomicLong version = new AtomicLong(0);
        private final ConcurrentMap<String, SceneObject> objects = new ConcurrentHashMap<>();

        private SceneState(String sceneId) {
            this.sceneId = sceneId;
        }
    }

    private static final class SceneObject {
        private final String id;
        private final String type;
        private final ConcurrentMap<String, Object> properties;
        private SceneRuntimeAdapter adapter;

        private SceneObject(String id, String type, Map<String, Object> properties) {
            this.id = id;
            this.type = type;
            this.properties = new ConcurrentHashMap<>(properties);
        }
    }

    public record SceneSnapshot(long version, java.util.List<MoudPackets.SceneObjectSnapshot> objects) {
    }

    public record SceneEditResult(boolean success, String message, MoudPackets.SceneObjectSnapshot snapshot, long version, String objectId) {
        public static SceneEditResult success(String objectId, MoudPackets.SceneObjectSnapshot snapshot, String message, long version) {
            return new SceneEditResult(true, Objects.requireNonNullElse(message, ""), snapshot, version, objectId);
        }

        public static SceneEditResult failure(String message, long version) {
            return new SceneEditResult(false, Objects.requireNonNullElse(message, "unknown error"), null, version, null);
        }
    }

    private void loadScenesFromDisk() {
        if (storageDirectory == null || !Files.isDirectory(storageDirectory)) {
            return;
        }
        try (var files = Files.list(storageDirectory)) {
            files.filter(path -> path.toString().endsWith(".json")).forEach(this::loadSceneFile);
        } catch (IOException e) {
            LOGGER.error("Failed to iterate scene directory", e);
        }
    }

    private void loadSceneFile(Path path) {
        try {
            PersistedScene persisted = mapper.readValue(path.toFile(), PersistedScene.class);
            SceneState state = new SceneState(persisted.getSceneId());
            scenes.put(persisted.getSceneId(), state);
            if (persisted.getObjects() != null) {
                persisted.getObjects().forEach(snapshot -> {
                    SceneObject object = new SceneObject(snapshot.getObjectId(), snapshot.getObjectType(), snapshot.getProperties());
                    state.objects.put(object.id, object);
                    try {
                        initializeAdapter(state.sceneId, object);
                    } catch (Exception e) {
                        LOGGER.error("Failed to spawn runtime object from persisted scene {}", state.sceneId, e);
                    }
                });
            }
            state.version.set(persisted.getVersion());
            LOGGER.info("Loaded scene '{}' with {} objects", persisted.getSceneId(), persisted.getObjects().size());
        } catch (Exception e) {
            LOGGER.error("Failed to load scene file {}", path, e);
        }
    }

    private void persistScene(String sceneId) {
        if (storageDirectory == null) {
            return;
        }
        SceneState state = scenes.get(sceneId);
        if (state == null) {
            return;
        }
        var objects = state.objects.values().stream()
                .map(SceneManager::toSnapshot)
                .collect(Collectors.toList());
        List<PersistedSceneObject> persistedObjects = objects.stream()
                .map(snapshot -> new PersistedSceneObject(snapshot.objectId(), snapshot.objectType(), snapshot.properties()))
                .collect(Collectors.toList());
        PersistedScene model = new PersistedScene(sceneId, state.version.get(), persistedObjects);
        Path file = storageDirectory.resolve(sceneId + ".json");
        try {
            mapper.writeValue(file.toFile(), model);
        } catch (IOException e) {
            LOGGER.error("Failed to persist scene {}", sceneId, e);
        }
    }

    public static class PersistedScene {
        private String sceneId;
        private long version;
        private java.util.List<PersistedSceneObject> objects;

        public PersistedScene() {}

        public PersistedScene(String sceneId, long version, java.util.List<PersistedSceneObject> objects) {
            this.sceneId = sceneId;
            this.version = version;
            this.objects = objects;
        }

        public String getSceneId() {
            return sceneId;
        }

        public void setSceneId(String sceneId) {
            this.sceneId = sceneId;
        }

        public String sceneId() {
            return getSceneId();
        }

        public long getVersion() {
            return version;
        }

        public void setVersion(long version) {
            this.version = version;
        }

        public long version() {
            return getVersion();
        }

        public java.util.List<PersistedSceneObject> getObjects() {
            return objects;
        }

        public void setObjects(java.util.List<PersistedSceneObject> objects) {
            this.objects = objects;
        }

        public java.util.List<PersistedSceneObject> objects() {
            return getObjects();
        }
    }

    public static class PersistedSceneObject {
        private String objectId;
        private String objectType;
        private Map<String, Object> properties;

        public PersistedSceneObject() {}

        public PersistedSceneObject(String objectId, String objectType, Map<String, Object> properties) {
            this.objectId = objectId;
            this.objectType = objectType;
            this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        }

        public String getObjectId() {
            return objectId;
        }

        public void setObjectId(String objectId) {
            this.objectId = objectId;
        }

        public String objectId() {
            return getObjectId();
        }

        public String getObjectType() {
            return objectType;
        }

        public void setObjectType(String objectType) {
            this.objectType = objectType;
        }

        public String objectType() {
            return getObjectType();
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        }

        public Map<String, Object> properties() {
            return getProperties();
        }
    }
}
