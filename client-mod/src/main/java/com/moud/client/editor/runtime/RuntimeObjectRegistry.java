package com.moud.client.editor.runtime;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.client.display.DisplaySurface;
import com.moud.client.model.RenderableModel;
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
        if (objectId.startsWith("camera-")) {
            com.moud.client.api.service.CameraService cameraService = com.moud.client.api.service.ClientAPIService.INSTANCE.camera;
            if (cameraService.isCustomCameraActive() && objectId.equals(cameraService.getActiveCameraId())) {
                com.moud.client.editor.scene.SceneObject sceneObject = com.moud.client.editor.scene.SceneSessionManager.getInstance().getSceneGraph().get(objectId);
                if (sceneObject != null) {
                    Map<String, Object> options = new java.util.HashMap<>();
                    Map<String, Object> props = sceneObject.getProperties();

                    Object posObj = props.get("position");
                    if (posObj != null) options.put("position", posObj);

                    Object rotObj = props.get("rotation");
                    if (rotObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rotMap = (Map<String, Object>) rotObj;
                        options.put("pitch", rotMap.get("pitch"));
                        options.put("yaw", rotMap.get("yaw"));
                        options.put("roll", rotMap.get("roll"));
                    }

                    Object fovObj = props.get("fov");
                    if (fovObj != null) options.put("fov", fovObj);
                    
                    if (!options.isEmpty()) {
                        cameraService.snapToFromMap(options);
                    }
                }
            }
            return; // Done for cameras.
        }

        RuntimeObject obj = objects.get(objectId);
        if (obj == null || propertyKey == null) {
            return;
        }

        if (obj.getType() == RuntimeObjectType.PLAYER_MODEL && propertyKey.startsWith("fakeplayer:")) {
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
            case "opacity":
                if (obj.getType() == RuntimeObjectType.DISPLAY) {
                    com.moud.client.display.DisplaySurface surface = com.moud.client.display.ClientDisplayManager.getInstance().getById(obj.getRuntimeId());
                    if (surface != null) {
                        surface.setOpacity(value);
                    }
                }
                break;
            case "intensity":
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.brightness = value;
                    }
                }
                break;
            case "range":
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.radius = value;
                    }
                }
                break;
            case "color.r":
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.r = value;
                    }
                }
                break;
            case "color.g":
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.g = value;
                    }
                }
                break;
            case "color.b":
                if (obj.getType() == RuntimeObjectType.LIGHT) {
                    com.moud.client.lighting.ClientLightingService.ManagedLight light = com.moud.client.lighting.ClientLightingService.getInstance().getManagedLight(obj.getRuntimeId());
                    if (light != null) {
                        light.b = value;
                    }
                }
                break;
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
        String rest = propertyKey.substring("fakeplayer:".length());
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
        if (model == null || model.getFakePlayer() == null) return;

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        var part = com.moud.client.animation.PlayerPartConfigManager.getInstance().getPartConfig(model.getFakePlayer().getUuid(), bone);
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
                    .updatePartConfig(model.getFakePlayer().getUuid(), bone, updates);
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
}
