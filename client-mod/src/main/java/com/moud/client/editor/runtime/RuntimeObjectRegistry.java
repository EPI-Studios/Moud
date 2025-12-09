package com.moud.client.editor.runtime;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.display.DisplaySurface;
import com.moud.client.model.RenderableModel;
import com.moud.client.editor.scene.SceneObject;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeObjectRegistry {
    private static final RuntimeObjectRegistry INSTANCE = new RuntimeObjectRegistry();

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
        String objectId = lightIndex.remove(lightId);
        if (objectId != null) {
            objects.remove(objectId);
        }
    }

    public void syncEmitter(com.moud.client.editor.scene.SceneObject sceneObject) {
        if (sceneObject == null) {
            return;
        }
        String objectId = sceneObject.getId();
        RuntimeObject runtime = objects.computeIfAbsent(objectId, id -> new RuntimeObject(RuntimeObjectType.PARTICLE_EMITTER, id.hashCode()));
        Map<String, Object> props = new java.util.HashMap<>(sceneObject.getProperties());
        Vec3d pos = parseVec3(props.get("position"), runtime.getPosition());
        if (pos != null) runtime.setPosition(pos);
        Vec3d rot = parseRotation(props.get("rotation"), runtime.getRotation());
        if (rot != null) runtime.setRotation(rot);
        runtime.setScale(new Vec3d(1, 1, 1));
        emitterIndex.put(objectId, objectId);
        upsertEmitterFromProps(objectId, props);
    }

    public void removeEmitter(String objectId) {
        if (objectId == null) return;
        emitterIndex.remove(objectId);
        objects.remove(objectId);
        var emitterSystem = com.moud.client.MoudClientMod.getInstance().getParticleEmitterSystem();
        if (emitterSystem != null) {
            emitterSystem.remove(java.util.List.of(objectId));
        }
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
        list.sort(java.util.Comparator.comparing(RuntimeObject::getLabel));
        return list;
    }

    public RuntimeObject getById(String objectId) {
        return objects.get(objectId);
    }

    public RuntimeObject getByModelId(long modelId) {
        String objectId = modelIndex.get(modelId);
        return objectId != null ? objects.get(objectId) : null;
    }

    public RuntimeObject getByDisplayId(long displayId) {
        String objectId = displayIndex.get(displayId);
        return objectId != null ? objects.get(objectId) : null;
    }

    public RuntimeObject getByPlayer(UUID uuid) {
        String objectId = playerIndex.get(uuid);
        return objectId != null ? objects.get(objectId) : null;
    }

    public RuntimeObject getByPlayerModel(long modelId) {
        String objectId = playerModelIndex.get(modelId);
        return objectId != null ? objects.get(objectId) : null;
    }

    public void applyAnimationProperty(String objectId, String propertyKey, float value) {
        applyAnimationProperty(objectId, propertyKey, null, value);
    }

    public void applyAnimationProperty(String objectId, String propertyKey, com.moud.api.animation.PropertyTrack.PropertyType propertyType, float value) {
        // Always update the scene graph for cameras as well, so the editor wireframe matches animation,
        // and additionally drive the active custom camera if it is bound.
        RuntimeObject obj = objects.get(objectId);
        SceneObject sceneObject = com.moud.client.editor.scene.SceneSessionManager.getInstance().getSceneGraph().get(objectId);

        if (sceneObject != null && propertyKey != null) {
            Map<String, Object> merged = new java.util.HashMap<>(sceneObject.getProperties());
            applyNestedProperty(merged, propertyKey, value);
            sceneObject.overwriteProperties(merged);

            if ("particle_emitter".equalsIgnoreCase(sceneObject.getType())) {
                upsertEmitterFromProps(objectId, merged);
            }

            if (objectId.startsWith("camera-")) {
                com.moud.client.api.service.CameraService cameraService = com.moud.client.api.service.ClientAPIService.INSTANCE.camera;
                boolean firstBind = !cameraService.isCustomCameraActive() || !objectId.equals(cameraService.getActiveCameraId());
                if (firstBind) {
                    cameraService.enableCustomCamera(objectId);
                }
                if (cameraService.isCustomCameraActive() && objectId.equals(cameraService.getActiveCameraId())) {
                    Map<String, Object> options = new java.util.HashMap<>();
                    Object posObj = merged.get("position");
                    if (posObj != null) options.put("position", posObj);

                    Object rotObj = merged.get("rotation");
                    if (rotObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rotMap = (Map<String, Object>) rotObj;
                        Object pitch = rotMap.getOrDefault("pitch", rotMap.get("x"));
                        Object yaw = rotMap.getOrDefault("yaw", rotMap.get("y"));
                        Object roll = rotMap.getOrDefault("roll", rotMap.get("z"));
                        if (pitch != null) options.put("pitch", pitch);
                        if (yaw != null) options.put("yaw", yaw);
                        if (roll != null) options.put("roll", roll);
                    }

                    Object fovObj = merged.get("fov");
                    if (fovObj != null) options.put("fov", fovObj);

                    if (!options.isEmpty()) {
                        if (firstBind) {
                            // On first bind, snap to avoid being stuck, then subsequent updates blend.
                            cameraService.snapToFromMap(options);
                        } else {
                            cameraService.animateFromAnimation(options);
                        }
                    }
                }
            }
        }

        if (objectId.startsWith("camera-")) {
            // Cameras don't have runtime backings beyond the scene graph/custom camera, so stop here.
            return;
        }

        if (obj == null || propertyKey == null) {
            return;
        }

        if (obj.getType() == RuntimeObjectType.PLAYER_MODEL && propertyKey.startsWith("player_model:")) {
            applyLimbProperty(obj, propertyKey, value);
            return;
        }

        Vec3d pos = obj.getPosition();
        Vec3d rot = obj.getRotation();
        Vec3d scl = obj.getScale();
        boolean transformChanged = false;
        switch (propertyKey) {
            case "position.x" -> {
                obj.setPosition(new Vec3d(value, pos.y, pos.z));
                transformChanged = true;
            }
            case "position.y" -> {
                obj.setPosition(new Vec3d(pos.x, value, pos.z));
                transformChanged = true;
            }
            case "position.z" -> {
                obj.setPosition(new Vec3d(pos.x, pos.y, value));
                transformChanged = true;
            }
            case "rotation.x" -> {
                obj.setRotation(new Vec3d(value, rot.y, rot.z));
                transformChanged = true;
            }
            case "rotation.y" -> {
                obj.setRotation(new Vec3d(rot.x, value, rot.z));
                transformChanged = true;
            }
            case "rotation.z" -> {
                obj.setRotation(new Vec3d(rot.x, rot.y, value));
                transformChanged = true;
            }
            case "scale.x" -> {
                obj.setScale(new Vec3d(Math.max(0.0001, value), scl.y, scl.z));
                transformChanged = true;
            }
            case "scale.y" -> {
                obj.setScale(new Vec3d(scl.x, Math.max(0.0001, value), scl.z));
                transformChanged = true;
            }
            case "scale.z" -> {
                obj.setScale(new Vec3d(scl.x, scl.y, Math.max(0.0001, value)));
                transformChanged = true;
            }
            case "opacity" -> {
                if (obj.getType() == RuntimeObjectType.DISPLAY) {
                    com.moud.client.display.DisplaySurface surface = com.moud.client.display.ClientDisplayManager.getInstance().getById(obj.getRuntimeId());
                    if (surface != null) {
                        surface.setOpacity(value);
                    }
                }
            }
            case "intensity" -> {
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.brightness = value;
                    }
                }
            }
            case "range" -> {
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.radius = value;
                    }
                }
            }
            case "color.r" -> {
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.r = value;
                    }
                }
            }
            case "color.g" -> {
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.g = value;
                    }
                }
            }
            case "color.b" -> {
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.b = value;
                    }
                }
            }
            default -> {
            }
        }
        if (transformChanged) {
            applyTransformsToBackings(obj);
        }
    }

    private void applyTransformsToBackings(RuntimeObject obj) {
        if (obj.getType() == RuntimeObjectType.DISPLAY) {
            com.moud.client.display.DisplaySurface surface = com.moud.client.display.ClientDisplayManager.getInstance().getById(obj.getRuntimeId());
            if (surface != null) {
                Vec3d pos = obj.getPosition();
                Vec3d rot = obj.getRotation();
                Vec3d scl = obj.getScale();
                Vector3 posVec = pos != null ? new Vector3(pos.x, pos.y, pos.z) : null;
                Quaternion rotQuat = rot != null ? Quaternion.fromEuler((float) rot.x, (float) rot.y, (float) rot.z) : null;
                Vector3 scaleVec = scl != null ? new Vector3(scl.x, scl.y, scl.z) : null;
                surface.updateTransform(posVec, rotQuat, scaleVec);
            }
        } else if (obj.getType() == RuntimeObjectType.LIGHT) {
            com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
            if (light != null) {
                Vec3d pos = obj.getPosition();
                if (pos != null) {
                    light.targetPosition = pos;
                }
                Vec3d rot = obj.getRotation();
                if (rot != null && "area".equalsIgnoreCase(light.type)) {
                    light.targetDirection = rotationToDirection(rot);
                }
            }
        }
    }

    private Vec3d rotationToDirection(Vec3d rotDeg) {
        double yaw = Math.toRadians(rotDeg.y);
        double pitch = Math.toRadians(rotDeg.x);
        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = -Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);
        Vec3d dir = new Vec3d(x, y, z);
        if (dir.lengthSquared() < 1e-6) {
            return new Vec3d(0.0, -1.0, 0.0);
        }
        return dir.normalize();
    }

    private void applyLimbProperty(RuntimeObject obj, String propertyKey, float value) {
        String rest = propertyKey.substring("player_model:".length());
        String[] parts = rest.split("\\.", 3);
        if (parts.length < 2) {
            return;
        }
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

        long modelId = -1;
        int idx = obj.getObjectId().indexOf(':');
        if (idx >= 0) {
            try {
                modelId = Long.parseLong(obj.getObjectId().substring(idx + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        if (modelId < 0) return;

        var model = com.moud.client.animation.ClientPlayerModelManager.getInstance().getModel(modelId);
        if (model == null || model.getEntity() == null) return;

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        var part = com.moud.client.animation.PlayerPartConfigManager.getInstance().getPartConfig(model.getEntity().getUuid(), bone);
        com.moud.api.math.Vector3 pos = part != null ? part.getInterpolatedPosition() : null;
        com.moud.api.math.Vector3 rot = part != null ? part.getInterpolatedRotation() : null;
        com.moud.api.math.Vector3 scale = part != null ? part.getInterpolatedScale() : null;

        switch (category) {
            case "position" -> {
                double x = pos != null ? pos.x : 0;
                double y = pos != null ? pos.y : 0;
                double z = pos != null ? pos.z : 0;
                if ("x".equals(component)) x = value;
                if ("y".equals(component)) y = value;
                if ("z".equals(component)) z = value;
                updates.put("position", new com.moud.api.math.Vector3(x, y, z));
            }
            case "rotation" -> {
                double x = rot != null ? rot.x : 0;
                double y = rot != null ? rot.y : 0;
                double z = rot != null ? rot.z : 0;
                if ("x".equals(component)) x = value;
                if ("y".equals(component)) y = value;
                if ("z".equals(component)) z = value;
                updates.put("rotation", new com.moud.api.math.Vector3(x, y, z));
            }
            case "scale" -> {
                double x = scale != null ? scale.x : 1;
                double y = scale != null ? scale.y : 1;
                double z = scale != null ? scale.z : 1;
                if ("x".equals(component)) x = Math.max(0.0001, value);
                if ("y".equals(component)) y = Math.max(0.0001, value);
                if ("z".equals(component)) z = Math.max(0.0001, value);
                updates.put("scale", new com.moud.api.math.Vector3(x, y, z));
            }
            default -> {
            }
        }

        if (!updates.isEmpty()) {
            com.moud.client.animation.PlayerPartConfigManager.getInstance()
                    .updatePartConfig(model.getEntity().getUuid(), bone, updates);
        }
    }

    public void syncModel(RenderableModel model) {
        if (model == null) {
            return;
        }
        RuntimeObject object = getOrCreate(RuntimeObjectType.MODEL, model.getId());
        object.updateFromModel(model);
        if (model.hasCollisionBoxes()) {
            object.updateBoundsFromCollision(model.getCollisionBoxes());
        }
        modelIndex.put(model.getId(), object.getObjectId());
    }

    public void removeModel(long modelId) {
        String objectId = modelIndex.remove(modelId);
        if (objectId != null) {
            objects.remove(objectId);
        }
    }

    public void syncPlayerModel(long modelId, Vec3d position, Vec3d rotationDeg) {
        RuntimeObject object = getOrCreate(RuntimeObjectType.PLAYER_MODEL, modelId);
        object.updateFromPlayerModel(position, rotationDeg);
        playerModelIndex.put(modelId, object.getObjectId());
    }

    public void removePlayerModel(long modelId) {
        String objectId = playerModelIndex.remove(modelId);
        if (objectId != null) {
            objects.remove(objectId);
        }
    }

    public void syncDisplay(DisplaySurface surface) {
        if (surface == null) {
            return;
        }
        RuntimeObject object = getOrCreate(RuntimeObjectType.DISPLAY, surface.getId());
        object.updateFromDisplay(surface);
        displayIndex.put(surface.getId(), object.getObjectId());
    }

    public void removeDisplay(long displayId) {
        String objectId = displayIndex.remove(displayId);
        if (objectId != null) {
            objects.remove(objectId);
        }
    }

    public void syncPlayers(List<? extends AbstractClientPlayerEntity> players) {
        Set<UUID> seen = new HashSet<>();
        for (AbstractClientPlayerEntity player : players) {
            if (player == null) {
                continue;
            }
            UUID uuid = player.getUuid();
            seen.add(uuid);
            String key = "player:" + uuid;
            RuntimeObject object = objects.computeIfAbsent(key,
                    id -> new RuntimeObject(RuntimeObjectType.PLAYER,
                            uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits(),
                            key));
            object.updateFromPlayer(player);
            playerIndex.put(uuid, key);
        }

        playerIndex.keySet().removeIf(uuid -> {
            if (!seen.contains(uuid)) {
                String objectId = playerIndex.get(uuid);
                if (objectId != null) {
                    objects.remove(objectId);
                }
                return true;
            }
            return false;
        });
    }

    public RuntimeObject pickRuntime(Vec3d origin, Vec3d direction, double maxDistance) {
        RuntimeObject best = null;
        double bestDistance = maxDistance;
        for (RuntimeObject object : objects.values()) {
            Box box = object.getBounds();
            if (box == null) {
                continue;
            }
            double distance = rayIntersectAabb(origin, direction, box);
            if (distance >= 0 && distance < bestDistance) {
                bestDistance = distance;
                best = object;
            }
        }
        return best;
    }

    private RuntimeObject getOrCreate(RuntimeObjectType type, long runtimeId) {
        String objectId = type.name().toLowerCase() + ":" + runtimeId;
        return objects.computeIfAbsent(objectId, id -> new RuntimeObject(type, runtimeId));
    }

    private double rayIntersectAabb(Vec3d origin, Vec3d direction, Box box) {
        double invX = 1.0 / (direction.x == 0 ? 1e-6 : direction.x);
        double invY = 1.0 / (direction.y == 0 ? 1e-6 : direction.y);
        double invZ = 1.0 / (direction.z == 0 ? 1e-6 : direction.z);

        double t1 = (box.minX - origin.x) * invX;
        double t2 = (box.maxX - origin.x) * invX;
        double tmin = Math.min(t1, t2);
        double tmax = Math.max(t1, t2);

        double ty1 = (box.minY - origin.y) * invY;
        double ty2 = (box.maxY - origin.y) * invY;
        tmin = Math.max(tmin, Math.min(ty1, ty2));
        tmax = Math.min(tmax, Math.max(ty1, ty2));

        double tz1 = (box.minZ - origin.z) * invZ;
        double tz2 = (box.maxZ - origin.z) * invZ;
        tmin = Math.max(tmin, Math.min(tz1, tz2));
        tmax = Math.min(tmax, Math.max(tz1, tz2));

        if (tmax < 0 || tmin > tmax) {
            return -1;
        }
        return tmin >= 0 ? tmin : tmax;
    }

    @SuppressWarnings("unchecked")
    private void applyNestedProperty(Map<String, Object> props, String key, float value) {
        if (key.contains(".")) {
            String[] parts = key.split("\\.");
            if (parts.length >= 2) {
                String root = parts[0];
                Map<String, Object> nested;
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

    private Vec3d parseVec3(Object obj, Vec3d fallback) {
        if (obj instanceof Map<?, ?> map) {
            double x = asDouble(map.get("x"), fallback != null ? fallback.x : 0);
            double y = asDouble(map.get("y"), fallback != null ? fallback.y : 0);
            double z = asDouble(map.get("z"), fallback != null ? fallback.z : 0);
            return new Vec3d(x, y, z);
        }
        return fallback;
    }

    private Vec3d parseRotation(Object obj, Vec3d fallback) {
        if (obj instanceof Map<?, ?> map) {
            Object ox = map.containsKey("pitch") ? map.get("pitch") : map.get("x");
            Object oy = map.containsKey("yaw") ? map.get("yaw") : map.get("y");
            Object oz = map.containsKey("roll") ? map.get("roll") : map.get("z");
            double x = asDouble(ox, fallback != null ? fallback.x : 0);
            double y = asDouble(oy, fallback != null ? fallback.y : 0);
            double z = asDouble(oz, fallback != null ? fallback.z : 0);
            return new Vec3d(x, y, z);
        }
        return fallback;
    }

    private double asDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o != null ? Double.parseDouble(String.valueOf(o)) : def;
        } catch (Exception ignored) {
            return def;
        }
    }

    private void upsertEmitterFromProps(String emitterId, Map<String, Object> props) {
        com.moud.client.particle.ParticleEmitterSystem system = com.moud.client.MoudClientMod.getInstance().getParticleEmitterSystem();
        if (system == null) return;

        com.moud.api.particle.ParticleDescriptor descriptor = com.moud.client.util.ParticleDescriptorMapper.fromMap(emitterId, props);
        if (descriptor == null) return;

        float rate = (float) asDouble(props.getOrDefault("rate", props.getOrDefault("spawnRate", 10f)), 10f);
        boolean enabled = props.getOrDefault("enabled", Boolean.TRUE) instanceof Boolean b ? b : true;
        int maxParticles = (int) asDouble(props.getOrDefault("maxParticles", 1024), 1024);
        com.moud.api.particle.Vector3f posJitter = com.moud.client.util.ParticleDescriptorMapper.vec3f(props.get("positionJitter"), new com.moud.api.particle.Vector3f(0f, 0f, 0f));
        com.moud.api.particle.Vector3f velJitter = com.moud.client.util.ParticleDescriptorMapper.vec3f(props.get("velocityJitter"), new com.moud.api.particle.Vector3f(0f, 0f, 0f));
        float lifetimeJitter = (float) asDouble(props.getOrDefault("lifetimeJitter", 0f), 0f);
        long seed = props.get("seed") instanceof Number n ? n.longValue() : System.currentTimeMillis();
        java.util.List<String> textures = com.moud.client.util.ParticleDescriptorMapper.stringList(props.get("textures"));

        com.moud.api.particle.ParticleEmitterConfig config = new com.moud.api.particle.ParticleEmitterConfig(
                emitterId,
                descriptor,
                rate,
                enabled,
                maxParticles,
                posJitter,
                velJitter,
                lifetimeJitter,
                seed,
                textures
        );
        system.upsert(java.util.List.of(config));
    }
}
