package com.moud.client.editor.runtime;

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

    private RuntimeObjectRegistry() {}

    public static RuntimeObjectRegistry getInstance() {
        return INSTANCE;
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

    public void syncModel(RenderableModel model) {
        if (model == null) {
            return;
        }
        RuntimeObject object = getOrCreate(RuntimeObjectType.MODEL, model.getId());
        object.updateFromModel(model);
        modelIndex.put(model.getId(), object.getObjectId());
    }

    public void removeModel(long modelId) {
        String objectId = modelIndex.remove(modelId);
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
