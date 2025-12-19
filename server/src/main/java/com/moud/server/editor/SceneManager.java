package com.moud.server.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.editor.runtime.SceneRuntimeAdapter;
import com.moud.server.editor.runtime.SceneRuntimeFactory;
import com.moud.server.editor.runtime.PlayerModelRuntimeAdapter;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.rendering.FogUniformRegistry;

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

    private static SceneManager instance;

    private final ConcurrentMap<String, SceneState> scenes = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private Path storageDirectory;
    private Path projectRoot;
    private com.moud.server.assets.AssetManager assetManager;

    public static synchronized void install(SceneManager sceneManager) {
        instance = Objects.requireNonNull(sceneManager, "sceneManager");
    }

    public SceneManager(Path projectRoot, com.moud.server.assets.AssetManager assetManager) {
        this.assetManager = assetManager;
        initialize(projectRoot);
    }

    private SceneManager() {
    }

    public static SceneManager getInstance() {
        if (instance == null) {
            instance = new SceneManager();
        }
        return instance;
    }

    public synchronized void initialize(Path projectRoot) {
        if (storageDirectory != null) {
            return;
        }
        this.projectRoot = projectRoot;
        storageDirectory = projectRoot.resolve(".moud").resolve("scenes");
        try {
            Files.createDirectories(storageDirectory);
            seedBaseSceneTemplate();
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

    public SceneObject getSceneObject(String sceneId, String objectId) {
        SceneState state = scenes.get(sceneId);
        if (state == null || objectId == null) {
            return null;
        }
        return state.objects.get(objectId);
    }

    public void applyAnimationFrame(String sceneId, String objectId, AnimationManager.TransformUpdate update) {
        if (update == null) {
            return;
        }
        SceneState state = scenes.get(sceneId);
        if (state == null || objectId == null) {
            return;
        }
        SceneObject obj = state.objects.get(objectId);
        if (obj == null) {
            return;
        }

        boolean changed = false;
        Vector3 position = update.positionIfChanged();
        Vector3 rotation = update.rotationEulerIfChanged();
        Quaternion rotationQuat = update.rotationQuatIfChanged();
        Vector3 scale = update.scaleIfChanged();
        Map<String, Object> props = obj.properties;

        if (position != null) {
            props.put("position", vectorToMap(position));
            changed = true;
        }
        if (rotation != null) {
            props.put("rotation", rotationToMap(rotation));
            changed = true;
        }
        if (rotationQuat != null) {
            props.put("rotationQuat", quaternionToMap(rotationQuat));
            changed = true;
        }
        if (scale != null) {
            props.put("scale", vectorToMap(scale));
            changed = true;
        }

        Map<String, Float> scalars = update.scalarProperties();
        if (scalars != null && !scalars.isEmpty()) {
            scalars.forEach((key, value) -> {
                if (key != null) {
                    applyNestedProperty(props, key, value);
                }
            });
            changed = true;
        }

        if (!changed) {
            return;
        }

        WorldTransform worldTransform = computeWorldTransform(sceneId, objectId, props, new java.util.HashSet<>());
        long version = state.version.incrementAndGet();

        try {
            initializeAdapter(sceneId, obj, toRuntimeSnapshot(sceneId, obj, worldTransform));
        } catch (Exception e) {
            LOGGER.warn("Failed to apply animation frame for {} in scene {}", objectId, sceneId, e);
        }

        ServerNetworkManager net = ServerNetworkManager.getInstance();
        if (net != null) {
            Map<String, Float> payload = (scalars == null || scalars.isEmpty()) ? null : new java.util.HashMap<>(scalars);
            net.broadcast(new MoudPackets.AnimationTransformUpdatePacket(
                    sceneId,
                    objectId,
                    position,
                    rotation,
                    rotationQuat,
                    scale,
                    payload
            ));
        }
    }

    public void applyAnimationProperty(String sceneId, String objectId, String propertyKey, com.moud.api.animation.PropertyTrack.PropertyType propertyType, float value) {
        SceneState state = scenes.get(sceneId);
        if (state == null || objectId == null || propertyKey == null) {
            return;
        }
        SceneObject obj = state.objects.get(objectId);
        if (obj == null) {
            return;
        }
        applyNestedProperty(obj.properties, propertyKey, value);
        long version = state.version.incrementAndGet();
        if (obj.adapter != null) {
            try {
                obj.adapter.update(toSnapshot(obj));
            } catch (Exception e) {
                LOGGER.warn("Failed to apply animation update to {}", objectId, e);
            }
        }
        com.moud.server.network.ServerNetworkManager net = com.moud.server.network.ServerNetworkManager.getInstance();
        if (net != null) {
            net.broadcast(new MoudPackets.AnimationPropertyUpdatePacket(sceneId, objectId, propertyKey, propertyType, value, buildPayload(obj.properties, propertyKey)));
        }
    }

    @SuppressWarnings("unchecked")
    private void applyNestedProperty(Map<String, Object> props, String key, float value) {
        if (key.contains(".")) {
            String[] parts = key.split("\\.");
            if (parts.length >= 2) {
                String root = parts[0];
                Map<String, Object> nested = null;
                Object existingRoot = props.get(root);
                if (existingRoot instanceof Map<?, ?> m) {
                    nested = (Map<String, Object>) m;
                } else {
                    nested = new java.util.HashMap<>();
                    props.put(root, nested);
                }
                if (parts.length == 2) {
                    nested.put(parts[1], value);
                } else {
                    Map<String, Object> current = nested;
                    for (int i = 1; i < parts.length - 1; i++) {
                        Object child = current.get(parts[i]);
                        if (child instanceof Map<?, ?> mm) {
                            current = (Map<String, Object>) mm;
                        } else {
                            Map<String, Object> newChild = new java.util.HashMap<>();
                            current.put(parts[i], newChild);
                            current = newChild;
                        }
                    }
                    current.put(parts[parts.length - 1], value);
                }
                return;
            }
        }
        props.put(key, value);
    }

    private Map<String, Object> vectorToMap(Vector3 vec) {
        return Map.of(
                "x", vec.x,
                "y", vec.y,
                "z", vec.z
        );
    }

    private Map<String, Object> rotationToMap(Vector3 vec) {
        return Map.of(
                "pitch", vec.x,
                "yaw", vec.y,
                "roll", vec.z
        );
    }

    private Map<String, Object> quaternionToMap(Quaternion quat) {
        return Map.of(
                "x", quat.x,
                "y", quat.y,
                "z", quat.z,
                "w", quat.w
        );
    }

    private Vector3 vectorProperty(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), fallback != null ? fallback.x : 0.0);
            double y = toDouble(map.get("y"), fallback != null ? fallback.y : 0.0);
            double z = toDouble(map.get("z"), fallback != null ? fallback.z : 0.0);
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private Vector3 rotationProperty(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            boolean hasEuler = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            double x = toDouble(hasEuler ? map.get("pitch") : map.get("x"), fallback != null ? fallback.x : 0.0);
            double y = toDouble(hasEuler ? map.get("yaw") : map.get("y"), fallback != null ? fallback.y : 0.0);
            double z = toDouble(hasEuler ? map.get("roll") : map.get("z"), fallback != null ? fallback.z : 0.0);
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private Quaternion quaternionProperty(Object raw, Quaternion fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), 0.0);
            double y = toDouble(map.get("y"), 0.0);
            double z = toDouble(map.get("z"), 0.0);
            double w = toDouble(map.get("w"), 1.0);
            return new Quaternion((float) x, (float) y, (float) z, (float) w);
        }
        return fallback;
    }

    private double toDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return raw != null ? Double.parseDouble(raw.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String getParentId(Map<String, Object> props) {
        Object parent = props != null ? props.getOrDefault("parentId", props.get("parent")) : null;
        if (parent == null) {
            return null;
        }
        String id = String.valueOf(parent).trim();
        return id.isEmpty() ? null : id;
    }

    private WorldTransform computeWorldTransform(String sceneId, String objectId, Map<String, Object> props, java.util.Set<String> visited) {
        if (props == null) {
            return null;
        }
        if (visited.contains(objectId)) {
            return null;
        }
        visited.add(objectId);

        Vector3 localPos = vectorProperty(props.get("position"), Vector3.zero());
        Vector3 localEuler = rotationProperty(props.get("rotation"), Vector3.zero());
        Quaternion localQuat = quaternionProperty(props.get("rotationQuat"), Quaternion.fromEuler(localEuler.x, localEuler.y, localEuler.z));
        Vector3 localScale = vectorProperty(props.get("scale"), Vector3.one());

        String parentId = getParentId(props);
        if (parentId == null) {
            return new WorldTransform(localPos, localEuler, localQuat, localScale);
        }

        SceneState state = scenes.get(sceneId);
        SceneObject parentObj = state != null ? state.objects.get(parentId) : null;
        if (parentObj == null) {
            return new WorldTransform(localPos, localEuler, localQuat, localScale);
        }

        WorldTransform parentWorld = computeWorldTransform(sceneId, parentId, parentObj.properties, visited);
        if (parentWorld == null) {
            return new WorldTransform(localPos, localEuler, localQuat, localScale);
        }

        Vector3 scaledLocal = localPos.multiply(parentWorld.scale());
        Vector3 rotatedLocal = parentWorld.quaternion().rotate(scaledLocal);
        Vector3 worldPos = parentWorld.position().add(rotatedLocal);

        Quaternion worldQuat = parentWorld.quaternion().multiply(localQuat).normalize();
        Vector3 worldEuler = worldQuat.toEuler();
        Vector3 worldScale = parentWorld.scale().multiply(localScale);

        return new WorldTransform(worldPos, worldEuler, worldQuat, worldScale);
    }

    private record WorldTransform(Vector3 position, Vector3 euler, Quaternion quaternion, Vector3 scale) {}

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPayload(Map<String, Object> props, String key) {
        if (props == null || key == null) {
            return null;
        }
        if (key.contains(".")) {
            String root = key.substring(0, key.indexOf('.'));
            Object rootVal = props.get(root);
            if (rootVal instanceof Map<?, ?> map) {
                return new java.util.HashMap<>((Map<String, Object>) map);
            }
        } else {
            Object val = props.get(key);
            if (val instanceof Map<?, ?> map) {
                return new java.util.HashMap<>((Map<String, Object>) map);
            }
        }
        return null;
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
        assets.add(fakePlayerAsset());

        return assets;
    }

    public List<MoudPackets.ProjectFileEntry> getProjectFileEntries() {
        if (projectRoot == null) {
            return List.of();
        }
        List<MoudPackets.ProjectFileEntry> entries = new ArrayList<>();
        scanProjectDirectory(projectRoot.resolve("assets"), entries);
        scanProjectDirectory(projectRoot.resolve("src"), entries);
        scanProjectDirectory(projectRoot.resolve("animations"), entries);
        return entries;
    }

    private void scanProjectDirectory(Path directory, List<MoudPackets.ProjectFileEntry> entries) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try {
            Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String relative = projectRoot.relativize(path).toString().replace('\\', '/');
                        entries.add(new MoudPackets.ProjectFileEntry(relative, MoudPackets.ProjectEntryKind.FILE));
                    });
        } catch (IOException e) {
            LOGGER.warn("Failed to scan project directory {}", directory, e);
        }
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

    private MoudPackets.EditorAssetDefinition fakePlayerAsset() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("skinUrl", "https://textures.minecraft.net/texture/45c338913be11c119f0e90a962f8d833b0dff78eaefdd8f2fa2a3434a1f2af0");
        defaults.put("label", "Fake Player");
        defaults.put("objectType", "player_model");
        defaults.put("width", 0.6);
        defaults.put("height", 1.8);
        defaults.put("physicsEnabled", false);
        defaults.put("sneaking", false);
        defaults.put("sprinting", false);
        defaults.put("swinging", false);
        defaults.put("usingItem", false);
        defaults.put("pathSpeed", 0.0);
        defaults.put("pathLoop", false);
        defaults.put("pathPingPong", false);
        return new MoudPackets.EditorAssetDefinition("player/fake_default", "Fake Player", "player_model", defaults);
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
        WorldTransform transform = computeWorldTransform(sceneId, object.id, object.properties, new java.util.HashSet<>());
        initializeAdapter(sceneId, object, toRuntimeSnapshot(sceneId, object, transform));
    }

    private void initializeAdapter(String sceneId, SceneObject object, MoudPackets.SceneObjectSnapshot snapshotOverride) throws Exception {
        if (object.adapter == null) {
            SceneRuntimeAdapter adapter = SceneRuntimeFactory.create(sceneId, object.type);
            object.adapter = adapter;
            if (adapter != null) {
                adapter.create(snapshotOverride != null ? snapshotOverride : toSnapshot(object));
            }
        } else if (object.adapter != null) {
            object.adapter.update(snapshotOverride != null ? snapshotOverride : toSnapshot(object));
        }
    }

    private static MoudPackets.SceneObjectSnapshot toSnapshot(SceneObject object) {
        return new MoudPackets.SceneObjectSnapshot(object.id, object.type, new ConcurrentHashMap<>(object.properties));
    }

    private MoudPackets.SceneObjectSnapshot toRuntimeSnapshot(String sceneId, SceneObject object, WorldTransform transform) {
        ConcurrentHashMap<String, Object> copy = new ConcurrentHashMap<>(object.properties);
        if (transform != null) {
            copy.put("position", vectorToMap(transform.position()));
            copy.put("rotation", rotationToMap(transform.euler()));
            copy.put("rotationQuat", quaternionToMap(transform.quaternion()));
            copy.put("scale", vectorToMap(transform.scale()));
        }
        return new MoudPackets.SceneObjectSnapshot(object.id, object.type, copy);
    }

    private static final class SceneState {
        private final String sceneId;
        private final AtomicLong version = new AtomicLong(0);
        private final ConcurrentMap<String, SceneObject> objects = new ConcurrentHashMap<>();

        private SceneState(String sceneId) {
            this.sceneId = sceneId;
        }
    }

    static final class SceneObject {
        private final String id;
        private final String type;
        private final ConcurrentMap<String, Object> properties;
        private SceneRuntimeAdapter adapter;

        private SceneObject(String id, String type, Map<String, Object> properties) {
            this.id = id;
            this.type = type;
            this.properties = new ConcurrentHashMap<>(properties);
        }

        ConcurrentMap<String, Object> getProperties() {
            return properties;
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

    private void seedBaseSceneTemplate() throws IOException {
        Path defaultScene = storageDirectory.resolve(SceneDefaults.DEFAULT_SCENE_ID + ".json");
        if (Files.exists(defaultScene)) {
            return;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("label", "Base Terrain");
        properties.put("description", "1000x1000 flat grass scene.");
        properties.put("size", SceneDefaults.BASE_SCENE_SIZE_BLOCKS);
        properties.put("terrainHeight", SceneDefaults.BASE_TERRAIN_HEIGHT);
        properties.put("surfaceBlock", SceneDefaults.DEFAULT_SURFACE_BLOCK);
        properties.put("fillBlock", SceneDefaults.DEFAULT_FILL_BLOCK);
        properties.put("spawn", Map.of(
                "x", 0.5,
                "y", SceneDefaults.defaultSpawnY(),
                "z", 0.5
        ));

        PersistedSceneObject terrainDescriptor = new PersistedSceneObject(
                "scene-template-terrain",
                "terrain",
                properties
        );

        Map<String, Object> fogProps = new LinkedHashMap<>();
        fogProps.put("effectId", "veil:fog");
        fogProps.put("uniforms", FogUniformRegistry.defaultFog());
        PersistedSceneObject fogDescriptor = new PersistedSceneObject(
                "scene-template-fog",
                "post_effect",
                fogProps
        );

        PersistedScene template = new PersistedScene(
                SceneDefaults.DEFAULT_SCENE_ID,
                1L,
                List.of(terrainDescriptor, fogDescriptor)
        );

        mapper.writeValue(defaultScene.toFile(), template);
        LOGGER.info("Created base scene template at {}", defaultScene);
    }

    private void loadSceneFile(Path path) {
        try {
            PersistedScene persisted = mapper.readValue(path.toFile(), PersistedScene.class);
            SceneState state = new SceneState(persisted.getSceneId());
            scenes.put(persisted.getSceneId(), state);
            if (persisted.getObjects() != null) {
                persisted.getObjects().forEach(snapshot -> {
                    SceneObject object = new SceneObject(
                            snapshot.getObjectId(),
                            snapshot.getObjectType(),
                            snapshot.getProperties()
                    );
                    state.objects.put(object.id, object);
                });
            }
            state.version.set(persisted.getVersion());
            LOGGER.info("Loaded scene '{}' with {} objects", persisted.getSceneId(), persisted.getObjects().size());
        } catch (Exception e) {
            LOGGER.error("Failed to load scene file {}", path, e);
        }
    }

    public void initializeRuntimeAdapters() {
        scenes.forEach((sceneId, state) -> state.objects.values().forEach(object -> {
            try {
                initializeAdapter(sceneId, object);
            } catch (Exception e) {
                LOGGER.error("Failed to spawn runtime object from persisted scene {}", sceneId, e);
            }
        }));
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
                .map(snapshot -> new PersistedSceneObject(
                        snapshot.objectId(),
                        snapshot.objectType(),
                        snapshot.properties()
                ))
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
