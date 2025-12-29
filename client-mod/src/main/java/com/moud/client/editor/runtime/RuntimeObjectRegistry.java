package com.moud.client.editor.runtime;

import com.moud.api.animation.PropertyTrack;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.api.particle.ParticleDescriptor;
import com.moud.api.particle.ParticleEmitterConfig;
import com.moud.api.particle.Vector3f;
import com.moud.client.MoudClientMod;
import com.moud.client.animation.ClientFakePlayerManager;
import com.moud.client.animation.ClientPlayerModelManager;
import com.moud.client.animation.PlayerPartConfigManager;
import com.moud.client.api.service.CameraService;
import com.moud.client.api.service.ClientAPIService;
import com.moud.client.display.ClientDisplayManager;
import com.moud.client.display.DisplaySurface;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.lighting.ClientLightingService;
import com.moud.client.model.RenderableModel;
import com.moud.client.particle.ParticleEmitterSystem;
import com.moud.client.util.ParticleDescriptorMapper;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeObjectRegistry {
    private static final RuntimeObjectRegistry INSTANCE = new RuntimeObjectRegistry();

    private static final String PROP_POSITION = "position";
    private static final String PROP_ROTATION = "rotation";
    private static final String PROP_ROTATION_QUAT = "rotationQuat";
    private static final String PROP_SCALE = "scale";
    private static final String TYPE_EMITTER = "particle_emitter";
    private static final String TYPE_PLAYER_MODEL = "player_model";
    private static final String PREFIX_CAMERA = "camera-";
    private static final String PREFIX_PLAYER_MODEL_PROP = "player_model:";

    private final Map<String, RuntimeObject> objects = new ConcurrentHashMap<>();
    private final Map<Long, String> modelIndex = new ConcurrentHashMap<>();
    private final Map<Long, String> displayIndex = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerIndex = new ConcurrentHashMap<>();
    private final Map<Long, String> playerModelIndex = new ConcurrentHashMap<>();
    private final Map<Long, String> lightIndex = new ConcurrentHashMap<>();
    private final Map<String, String> emitterIndex = new ConcurrentHashMap<>();

    private RuntimeObjectRegistry() {}

    public static RuntimeObjectRegistry getInstance() {
        return INSTANCE;
    }

    public void syncLight(long lightId, Map<String, Object> properties) {
        RuntimeObject object = getOrCreate(RuntimeObjectType.LIGHT, lightId);
        object.updateFromMap(properties);
        lightIndex.put(lightId, object.getObjectId());
    }

    public void removeLight(long lightId) {
        removeObjectByIndex(lightIndex, lightId);
    }

    public void syncModel(RenderableModel model) {
        if (model == null) return;
        RuntimeObject object = getOrCreate(RuntimeObjectType.MODEL, model.getId());
        object.updateFromModel(model);
        if (model.hasCollisionBoxes()) {
            object.updateBoundsFromCollision(model.getCollisionBoxes());
        }
        modelIndex.put(model.getId(), object.getObjectId());
    }

    public void removeModel(long modelId) {
        removeObjectByIndex(modelIndex, modelId);
    }

    public void syncDisplay(DisplaySurface surface) {
        if (surface == null) return;
        RuntimeObject object = getOrCreate(RuntimeObjectType.DISPLAY, surface.getId());
        object.updateFromDisplay(surface);
        displayIndex.put(surface.getId(), object.getObjectId());
    }

    public void removeDisplay(long displayId) {
        removeObjectByIndex(displayIndex, displayId);
    }

    public void syncPlayerModel(long modelId, Vec3d position, Vec3d rotationDeg) {
        RuntimeObject object = getOrCreate(RuntimeObjectType.PLAYER_MODEL, modelId);
        object.updateFromPlayerModel(position, rotationDeg);
        playerModelIndex.put(modelId, object.getObjectId());
    }

    public void syncFakePlayer(long modelId, AbstractClientPlayerEntity player) {
        RuntimeObject object = getOrCreate(RuntimeObjectType.PLAYER_MODEL, modelId);
        object.updateFromPlayer(player);
        playerModelIndex.put(modelId, object.getObjectId());
    }

    public void removePlayerModel(long modelId) {
        removeObjectByIndex(playerModelIndex, modelId);
    }

    public void syncEmitter(SceneObject sceneObject) {
        if (sceneObject == null) return;
        String objectId = sceneObject.getId();

        RuntimeObject runtime = objects.computeIfAbsent(objectId, id ->
                new RuntimeObject(RuntimeObjectType.PARTICLE_EMITTER, id.hashCode())
        );

        Map<String, Object> props = new HashMap<>(sceneObject.getProperties());

        Vec3d pos = MapUtils.getAsVec3d(props.get(PROP_POSITION), runtime.getPosition());
        if (pos != null) runtime.setPosition(pos);

        Vec3d rot = MapUtils.getAsVec3dFromRot(props.get(PROP_ROTATION), runtime.getRotation());
        if (rot != null) runtime.setRotation(rot);

        runtime.setScale(new Vec3d(1, 1, 1));

        emitterIndex.put(objectId, objectId);
        upsertEmitterFromProps(objectId, props);
    }

    public void removeEmitter(String objectId) {
        if (objectId == null) return;
        emitterIndex.remove(objectId);
        objects.remove(objectId);

        ParticleEmitterSystem system = MoudClientMod.getInstance().getParticleEmitterSystem();
        if (system != null) {
            system.remove(List.of(objectId));
        }
    }

    public void syncPlayers(List<? extends AbstractClientPlayerEntity> players) {
        Set<UUID> currentUuids = new HashSet<>();

        for (AbstractClientPlayerEntity player : players) {
            if (player == null) continue;
            if (player instanceof OtherClientPlayerEntity other && ClientFakePlayerManager.isFakePlayer(other)) {
                continue;
            }

            UUID uuid = player.getUuid();
            currentUuids.add(uuid);
            String key = "player:" + uuid;

            RuntimeObject object = objects.computeIfAbsent(key, id ->
                    new RuntimeObject(RuntimeObjectType.PLAYER,
                            uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits(), key)
            );

            object.updateFromPlayer(player);
            playerIndex.put(uuid, key);
        }

        playerIndex.keySet().removeIf(uuid -> {
            if (!currentUuids.contains(uuid)) {
                String objectId = playerIndex.get(uuid);
                if (objectId != null) objects.remove(objectId);
                return true;
            }
            return false;
        });
    }

    public Collection<RuntimeObject> getObjects() {
        return Collections.unmodifiableCollection(objects.values());
    }

    public Collection<RuntimeObject> getObjects(RuntimeObjectType type) {
        List<RuntimeObject> list = new ArrayList<>();
        for (RuntimeObject object : objects.values()) {
            if (object.getType() == type) {
                list.add(object);
            }
        }
        list.sort(Comparator.comparing(RuntimeObject::getLabel));
        return list;
    }

    public RuntimeObject getById(String objectId) { return objects.get(objectId); }
    public RuntimeObject getByModelId(long modelId) { return lookup(modelIndex, modelId); }
    public RuntimeObject getByDisplayId(long displayId) { return lookup(displayIndex, displayId); }
    public RuntimeObject getByPlayer(UUID uuid) { return lookup(playerIndex, uuid); }
    public RuntimeObject getByPlayerModel(long modelId) { return lookup(playerModelIndex, modelId); }

    public RuntimeObject pickRuntime(Vec3d origin, Vec3d direction, double maxDistance) {
        RuntimeObject best = null;
        double bestDistance = maxDistance;

        for (RuntimeObject object : objects.values()) {
            Box box = object.getBounds();
            if (box == null) continue;

            double dist = MathUtils.intersectAabb(origin, direction, box);
            if (dist >= 0 && dist < bestDistance) {
                bestDistance = dist;
                best = object;
            }
        }
        return best;
    }

    public void applyAnimationTransform(String objectId,
                                        Vector3 position,
                                        Vector3 rotation,
                                        Quaternion rotationQuat,
                                        Vector3 scale,
                                        Map<String, Float> properties) {

        updateSceneObjectProperties(objectId, position, rotation, rotationQuat, scale, properties);

        if (objectId != null && objectId.startsWith(PREFIX_CAMERA)) {
            handleCameraAnimation(objectId, position, rotation, rotationQuat, properties);
            return;
        }

        RuntimeObject obj = resolveRuntimeObject(objectId);
        if (obj == null) {
            if (properties != null && !properties.isEmpty()) {
                properties.forEach((key, value) -> applyAnimationProperty(objectId, key, null, value));
            }
            return;
        }

        TransformResult world = calculateWorldTransform(objectId, position, rotation, rotationQuat, scale);

        boolean changed = false;
        if (world.pos != null) { obj.setPosition(world.pos); changed = true; }
        if (world.rot != null) { obj.setRotation(world.rot); changed = true; }
        if (world.scale != null) { obj.setScale(world.scale); changed = true; }

        if (changed) {
            applyTransformsToBackings(obj);
        }

        if (properties != null && !properties.isEmpty()) {
            properties.forEach((key, value) -> applyAnimationProperty(obj.getObjectId(), key, null, value));
        }
    }

    public void applyAnimationProperty(String objectId, String propertyKey, PropertyTrack.PropertyType propertyType, float value) {
        updateSceneObjectSingleProperty(objectId, propertyKey, value);

        if (objectId.startsWith(PREFIX_CAMERA)) {
            handleCameraProperty(objectId, propertyKey, value);
            return;
        }

        RuntimeObject obj = resolveRuntimeObject(objectId);
        if (obj == null || propertyKey == null) return;

        if (obj.getType() == RuntimeObjectType.PLAYER_MODEL && propertyKey.startsWith(PREFIX_PLAYER_MODEL_PROP)) {
            applyLimbProperty(obj, propertyKey, value);
            return;
        }

        boolean changed = applyTransformProperty(obj, propertyKey, value);
        if (!changed) {
            applySpecificProperty(obj, propertyKey, value);
        } else {
            applyTransformsToBackings(obj);
        }
    }

    private void updateSceneObjectProperties(String objectId, Vector3 pos, Vector3 rot, Quaternion quat, Vector3 scale, Map<String, Float> props) {
        SceneObject sceneObject = SceneSessionManager.getInstance().getSceneGraph().get(objectId);
        if (sceneObject == null) return;

        Map<String, Object> merged = new HashMap<>(sceneObject.getProperties());
        if (pos != null) merged.put(PROP_POSITION, MapUtils.toMap(pos));
        if (rot != null) merged.put(PROP_ROTATION, MapUtils.toMapRot(rot));
        if (quat != null) merged.put(PROP_ROTATION_QUAT, MapUtils.toMap(quat));
        if (scale != null) merged.put(PROP_SCALE, MapUtils.toMap(scale));

        if (props != null) {
            props.forEach((k, v) -> MapUtils.applyNested(merged, k, v));
        }

        sceneObject.overwriteProperties(merged);
        if (TYPE_EMITTER.equalsIgnoreCase(sceneObject.getType())) {
            upsertEmitterFromProps(objectId, merged);
        }
    }

    private void updateSceneObjectSingleProperty(String objectId, String key, float value) {
        SceneObject sceneObject = SceneSessionManager.getInstance().getSceneGraph().get(objectId);
        if (sceneObject == null || key == null) return;

        Map<String, Object> merged = new HashMap<>(sceneObject.getProperties());
        MapUtils.applyNested(merged, key, value);
        sceneObject.overwriteProperties(merged);

        if (TYPE_EMITTER.equalsIgnoreCase(sceneObject.getType())) {
            upsertEmitterFromProps(objectId, merged);
        }
    }

    private void handleCameraAnimation(String objectId, Vector3 pos, Vector3 rot, Quaternion quat, Map<String, Float> props) {
        CameraService service = ClientAPIService.INSTANCE.camera;
        if (service == null) return;

        boolean activate = !service.isCustomCameraActive() || !objectId.equals(service.getActiveCameraId());
        if (activate) service.enableCustomCamera(objectId);
        if (!service.isCustomCameraActive() || !objectId.equals(service.getActiveCameraId())) return;

        Map<String, Object> options = new HashMap<>();
        if (pos != null) options.put(PROP_POSITION, MapUtils.toMap(pos));

        Vector3 euler = rot != null ? rot : (quat != null ? quat.toEuler() : null);
        if (euler != null) {
            options.put("pitch", euler.x);
            options.put("yaw", euler.y);
            options.put("roll", euler.z);
        }

        if (props != null && props.containsKey("fov")) {
            options.put("fov", props.get("fov"));
        }

        if (!options.isEmpty()) {
            if (activate) service.snapToFromMap(options);
            else service.animateFromAnimation(options);
        }
    }

    private void handleCameraProperty(String objectId, String key, float value) {
        CameraService service = ClientAPIService.INSTANCE.camera;
        if (service == null) return;

        boolean activate = !service.isCustomCameraActive() || !objectId.equals(service.getActiveCameraId());
        if (activate) service.enableCustomCamera(objectId);
        if (!service.isCustomCameraActive() || !objectId.equals(service.getActiveCameraId())) return;

        Map<String, Object> options = new HashMap<>();
        MapUtils.applyNested(options, key, value);

        if (!options.isEmpty()) {
            if (activate) service.snapToFromMap(options);
            else service.animateFromAnimation(options);
        }
    }

    private boolean applyTransformProperty(RuntimeObject obj, String key, float val) {
        Vec3d pos = obj.getPosition();
        Vec3d rot = obj.getRotation();
        Vec3d scl = obj.getScale();

        switch (key) {
            case "position.x" -> obj.setPosition(new Vec3d(val, pos.y, pos.z));
            case "position.y" -> obj.setPosition(new Vec3d(pos.x, val, pos.z));
            case "position.z" -> obj.setPosition(new Vec3d(pos.x, pos.y, val));
            case "rotation.x" -> obj.setRotation(new Vec3d(val, rot.y, rot.z));
            case "rotation.y" -> obj.setRotation(new Vec3d(rot.x, val, rot.z));
            case "rotation.z" -> obj.setRotation(new Vec3d(rot.x, rot.y, val));
            case "scale.x" -> obj.setScale(new Vec3d(Math.max(0.0001, val), scl.y, scl.z));
            case "scale.y" -> obj.setScale(new Vec3d(scl.x, Math.max(0.0001, val), scl.z));
            case "scale.z" -> obj.setScale(new Vec3d(scl.x, scl.y, Math.max(0.0001, val)));
            default -> { return false; }
        }
        return true;
    }

    private void applySpecificProperty(RuntimeObject obj, String key, float val) {
        if (obj.getType() == RuntimeObjectType.DISPLAY && "opacity".equals(key)) {
            DisplaySurface surface = ClientDisplayManager.getInstance().getById(obj.getRuntimeId());
            if (surface != null) surface.setOpacity(val);
        } else if (obj.getType() == RuntimeObjectType.LIGHT) {
            ClientLightingService.ManagedLight light = ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
            if (light != null) {
                switch (key) {
                    case "intensity" -> light.brightness = val;
                    case "range" -> light.radius = val;
                    case "color.r" -> light.r = val;
                    case "color.g" -> light.g = val;
                    case "color.b" -> light.b = val;
                }
            }
        }
    }

    private void applyLimbProperty(RuntimeObject obj, String key, float val) {
        String subKey = key.substring(PREFIX_PLAYER_MODEL_PROP.length());
        String[] parts = subKey.split("\\.", 3);
        if (parts.length < 2) return;

        String limb = parts[0];
        String category = parts[1];
        String component = parts.length >= 3 ? parts[2] : null;

        String bone = switch (limb) {
            case "left_arm" -> "left_arm";
            case "right_arm" -> "right_arm";
            case "left_leg" -> "left_leg";
            case "right_leg" -> "right_leg";
            case "head" -> "head";
            case "torso" -> "body";
            default -> null;
        };
        if (bone == null) return;

        long modelId = parseModelId(obj.getObjectId());
        if (modelId < 0) return;

        var model = ClientPlayerModelManager.getInstance().getModel(modelId);
        if (model == null || model.getEntity() == null) return;

        var part = PlayerPartConfigManager.getInstance().getPartConfig(model.getEntity().getUuid(), bone);
        Map<String, Object> updates = new HashMap<>();

        Vector3 pos = part != null ? part.getInterpolatedPosition() : Vector3.zero();
        Vector3 rot = part != null ? part.getInterpolatedRotation() : Vector3.zero();
        Vector3 scale = part != null ? part.getInterpolatedScale() : Vector3.one();

        switch (category) {
            case "position" -> updates.put(PROP_POSITION, new Vector3(
                    "x".equals(component) ? val : pos.x,
                    "y".equals(component) ? val : pos.y,
                    "z".equals(component) ? val : pos.z
            ));
            case "rotation" -> updates.put(PROP_ROTATION, new Vector3(
                    "x".equals(component) ? val : rot.x,
                    "y".equals(component) ? val : rot.y,
                    "z".equals(component) ? val : rot.z
            ));
            case "scale" -> updates.put(PROP_SCALE, new Vector3(
                    "x".equals(component) ? Math.max(0.0001, val) : scale.x,
                    "y".equals(component) ? Math.max(0.0001, val) : scale.y,
                    "z".equals(component) ? Math.max(0.0001, val) : scale.z
            ));
        }

        if (!updates.isEmpty()) {
            PlayerPartConfigManager.getInstance().updatePartConfig(model.getEntity().getUuid(), bone, updates);
        }
    }

    private void applyTransformsToBackings(RuntimeObject obj) {
        if (obj.getType() == RuntimeObjectType.DISPLAY) {
            DisplaySurface surface = ClientDisplayManager.getInstance().getById(obj.getRuntimeId());
            if (surface != null) {
                Vec3d rot = obj.getRotation();
                surface.updateTransform(
                        MapUtils.toVector3(obj.getPosition()),
                        rot != null ? Quaternion.fromEuler((float) rot.x, (float) rot.y, (float) rot.z) : null,
                        MapUtils.toVector3(obj.getScale()),
                        surface.getBillboardMode(),
                        surface.isRenderThroughBlocks()
                );
            }
        } else if (obj.getType() == RuntimeObjectType.LIGHT) {
            ClientLightingService.ManagedLight light = ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
            if (light != null) {
                if (obj.getPosition() != null) light.targetPosition = obj.getPosition();
                Vec3d rot = obj.getRotation();
                if (rot != null && "area".equalsIgnoreCase(light.type)) {
                    light.targetDirection = MathUtils.rotationToDirection(rot);
                }
            }
        }
    }

    private RuntimeObject getOrCreate(RuntimeObjectType type, long runtimeId) {
        String objectId = type.name().toLowerCase() + ":" + runtimeId;
        return objects.computeIfAbsent(objectId, id -> new RuntimeObject(type, runtimeId));
    }

    private RuntimeObject resolveRuntimeObject(String objectId) {
        if (objectId == null) return null;
        RuntimeObject direct = objects.get(objectId);
        if (direct != null) return direct;

        SceneObject sceneObj = SceneSessionManager.getInstance().getSceneGraph().get(objectId);
        if (sceneObj == null || !TYPE_PLAYER_MODEL.equalsIgnoreCase(sceneObj.getType())) {
            return null;
        }

        Vec3d scenePos = MapUtils.getAsVec3d(sceneObj.getProperties().get(PROP_POSITION), null);
        if (scenePos == null) return null;

        RuntimeObject best = null;
        double bestDist = Double.MAX_VALUE;
        for (RuntimeObject candidate : objects.values()) {
            if (candidate.getType() != RuntimeObjectType.PLAYER_MODEL || candidate.getPosition() == null) continue;
            double dist = candidate.getPosition().squaredDistanceTo(scenePos);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private <K, V> void removeObjectByIndex(Map<K, V> indexMap, K key) {
        V objId = indexMap.remove(key);
        if (objId != null) objects.remove(objId);
    }

    private <K> RuntimeObject lookup(Map<K, String> index, K key) {
        String id = index.get(key);
        return id != null ? objects.get(id) : null;
    }

    private long parseModelId(String objectId) {
        int idx = objectId.indexOf(':');
        if (idx < 0) return -1;
        try {
            return Long.parseLong(objectId.substring(idx + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void upsertEmitterFromProps(String id, Map<String, Object> props) {
        ParticleEmitterSystem system = MoudClientMod.getInstance().getParticleEmitterSystem();
        if (system == null) return;

        ParticleDescriptor descriptor = ParticleDescriptorMapper.fromMap(id, props);
        if (descriptor == null) return;

        ParticleEmitterConfig config = new ParticleEmitterConfig(
                id,
                descriptor,
                (float) MapUtils.asDouble(props.getOrDefault("rate", props.get("spawnRate")), 10.0),
                MapUtils.asBoolean(props.get("enabled"), true),
                (int) MapUtils.asDouble(props.getOrDefault("maxParticles", 1024), 1024),
                ParticleDescriptorMapper.vec3f(props.get("positionJitter"), new Vector3f(0f, 0f, 0f)),
                ParticleDescriptorMapper.vec3f(props.get("velocityJitter"), new Vector3f(0f, 0f, 0f)),
                (float) MapUtils.asDouble(props.get("lifetimeJitter"), 0.0),
                props.get("seed") instanceof Number n ? n.longValue() : System.currentTimeMillis(),
                ParticleDescriptorMapper.stringList(props.get("textures"))
        );
        system.upsert(List.of(config));
    }

    private TransformResult calculateWorldTransform(String objectId, Vector3 pos, Vector3 rot, Quaternion quat, Vector3 scale) {
        return calculateWorldTransformRecursive(objectId, pos, rot, quat, scale, new HashSet<>());
    }

    private TransformResult calculateWorldTransformRecursive(String objectId, Vector3 localPos, Vector3 localEuler, Quaternion localQuat, Vector3 localScale, Set<String> visited) {
        if (!visited.add(objectId)) {
            return new TransformResult(
                    toVec3d(localPos),
                    localEuler != null ? toVec3d(localEuler) : null,
                    toVec3d(localScale)
            );
        }

        Vector3 p = localPos != null ? localPos : Vector3.zero();
        Quaternion q = localQuat != null ? localQuat : (localEuler != null ? Quaternion.fromEuler(localEuler.x, localEuler.y, localEuler.z) : Quaternion.identity());
        Vector3 s = localScale != null ? localScale : Vector3.one();

        SceneObject sceneObj = SceneSessionManager.getInstance().getSceneGraph().get(objectId);
        String parentId = sceneObj != null ? MapUtils.asString(sceneObj.getProperties().getOrDefault("parentId", sceneObj.getProperties().get("parent"))) : null;

        if (parentId == null) {
            return new TransformResult(toVec3d(p), toVec3d(q.toEuler()), toVec3d(s));
        }

        TransformResult parentRes;
        RuntimeObject parentRuntime = objects.get(parentId);

        if (parentRuntime != null && parentRuntime.getPosition() != null) {
            Vec3d pp = parentRuntime.getPosition();
            Vec3d pr = parentRuntime.getRotation();
            Vec3d ps = parentRuntime.getScale();
            parentRes = calculateWorldTransformRecursive(
                    parentId,
                    pp != null ? new Vector3(pp.x, pp.y, pp.z) : null,
                    pr != null ? new Vector3(pr.x, pr.y, pr.z) : null,
                    null,
                    ps != null ? new Vector3(ps.x, ps.y, ps.z) : null,
                    visited
            );
        } else {
            SceneObject parentScene = SceneSessionManager.getInstance().getSceneGraph().get(parentId);
            if (parentScene != null) {
                Map<String, Object> props = parentScene.getProperties();
                parentRes = calculateWorldTransformRecursive(
                        parentId,
                        MapUtils.getVector3(props.get(PROP_POSITION), Vector3.zero()),
                        MapUtils.getVector3FromRot(props.get(PROP_ROTATION), Vector3.zero()),
                        null,
                        MapUtils.getVector3(props.get(PROP_SCALE), Vector3.one()),
                        visited
                );
            } else {
                return new TransformResult(toVec3d(p), toVec3d(q.toEuler()), toVec3d(s));
            }
        }

        Vector3 parentScale = parentRes.scale != null ? new Vector3(parentRes.scale.x, parentRes.scale.y, parentRes.scale.z) : Vector3.one();
        Quaternion parentRot = parentRes.rot != null ? Quaternion.fromEuler((float)parentRes.rot.x, (float)parentRes.rot.y, (float)parentRes.rot.z) : Quaternion.identity();
        Vector3 parentPos = parentRes.pos != null ? new Vector3(parentRes.pos.x, parentRes.pos.y, parentRes.pos.z) : Vector3.zero();

        Vector3 scaledLocal = p.multiply(parentScale);
        Vector3 rotatedLocal = parentRot.rotate(scaledLocal);
        Vector3 finalPos = parentPos.add(rotatedLocal);
        Quaternion finalRot = parentRot.multiply(q).normalize();
        Vector3 finalScale = parentScale.multiply(s);

        return new TransformResult(toVec3d(finalPos), toVec3d(finalRot.toEuler()), toVec3d(finalScale));
    }

    private record TransformResult(Vec3d pos, Vec3d rot, Vec3d scale) {}

    private static Vec3d toVec3d(Vector3 v) { return v != null ? new Vec3d(v.x, v.y, v.z) : null; }

    private static final class MapUtils {
        static Vec3d getAsVec3d(Object o, Vec3d def) {
            if (o instanceof Map<?,?> m) return new Vec3d(asDouble(m.get("x"), 0), asDouble(m.get("y"), 0), asDouble(m.get("z"), 0));
            return def;
        }

        static Vec3d getAsVec3dFromRot(Object o, Vec3d def) {
            if (o instanceof Map<?,?> m) {
                boolean euler = m.containsKey("pitch") || m.containsKey("yaw");
                return new Vec3d(
                        asDouble(euler ? m.get("pitch") : m.get("x"), 0),
                        asDouble(euler ? m.get("yaw") : m.get("y"), 0),
                        asDouble(euler ? m.get("roll") : m.get("z"), 0)
                );
            }
            return def;
        }

        static Vector3 getVector3(Object o, Vector3 def) {
            Vec3d v = getAsVec3d(o, null);
            return v != null ? new Vector3(v.x, v.y, v.z) : def;
        }

        static Vector3 getVector3FromRot(Object o, Vector3 def) {
            Vec3d v = getAsVec3dFromRot(o, null);
            return v != null ? new Vector3(v.x, v.y, v.z) : def;
        }

        static double asDouble(Object o, double def) {
            if (o instanceof Number n) return n.doubleValue();
            try { return o != null ? Double.parseDouble(o.toString()) : def; } catch (Exception e) { return def; }
        }

        static boolean asBoolean(Object o, boolean def) {
            if (o instanceof Boolean b) return b;
            return o != null ? Boolean.parseBoolean(o.toString()) : def;
        }

        static String asString(Object o) { return o != null ? o.toString().trim() : null; }

        static Vector3 toVector3(Vec3d v) { return v != null ? new Vector3(v.x, v.y, v.z) : null; }

        static Map<String, Object> toMap(Vector3 v) {
            Map<String, Object> m = new HashMap<>();
            m.put("x", v.x); m.put("y", v.y); m.put("z", v.z);
            return m;
        }

        static Map<String, Object> toMapRot(Vector3 v) {
            Map<String, Object> m = new HashMap<>();
            m.put("pitch", v.x); m.put("yaw", v.y); m.put("roll", v.z);
            return m;
        }

        static Map<String, Object> toMap(Quaternion q) {
            Map<String, Object> m = new HashMap<>();
            m.put("x", q.x); m.put("y", q.y); m.put("z", q.z); m.put("w", q.w);
            return m;
        }

        @SuppressWarnings("unchecked")
        static void applyNested(Map<String, Object> map, String key, Object val) {
            String[] parts = key.split("\\.");
            Map<String, Object> cur = map;
            for (int i=0; i<parts.length-1; i++) {
                cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new HashMap<>());
            }
            cur.put(parts[parts.length-1], val);
        }
    }

    private static final class MathUtils {
        static double intersectAabb(Vec3d origin, Vec3d dir, Box box) {
            double invX = 1.0 / (dir.x == 0 ? 1e-6 : dir.x);
            double invY = 1.0 / (dir.y == 0 ? 1e-6 : dir.y);
            double invZ = 1.0 / (dir.z == 0 ? 1e-6 : dir.z);

            double t1 = (box.minX - origin.x) * invX, t2 = (box.maxX - origin.x) * invX;
            double tmin = Math.min(t1, t2), tmax = Math.max(t1, t2);

            double ty1 = (box.minY - origin.y) * invY, ty2 = (box.maxY - origin.y) * invY;
            tmin = Math.max(tmin, Math.min(ty1, ty2));
            tmax = Math.min(tmax, Math.max(ty1, ty2));

            double tz1 = (box.minZ - origin.z) * invZ, tz2 = (box.maxZ - origin.z) * invZ;
            tmin = Math.max(tmin, Math.min(tz1, tz2));
            tmax = Math.min(tmax, Math.max(tz1, tz2));

            return (tmax < 0 || tmin > tmax) ? -1 : (tmin >= 0 ? tmin : tmax);
        }

        static Vec3d rotationToDirection(Vec3d rot) {
            double y = Math.toRadians(rot.y);
            double p = Math.toRadians(rot.x);
            double dx = -Math.sin(y) * Math.cos(p);
            double dy = -Math.sin(p);
            double dz = Math.cos(y) * Math.cos(p);
            Vec3d d = new Vec3d(dx, dy, dz);
            return d.lengthSquared() < 1e-6 ? new Vec3d(0, -1, 0) : d.normalize();
        }
    }
}