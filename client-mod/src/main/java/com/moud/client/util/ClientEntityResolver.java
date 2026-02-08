package com.moud.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientEntityResolver {
    private static final Map<UUID, WeakReference<Entity>> CACHE = new ConcurrentHashMap<>();

    private ClientEntityResolver() {
    }

    public static Entity resolve(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        WeakReference<Entity> cachedRef = CACHE.get(uuid);
        if (cachedRef != null) {
            Entity cached = cachedRef.get();
            if (cached != null && !cached.isRemoved() && uuid.equals(cached.getUuid())) {
                return cached;
            }
        }

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            CACHE.remove(uuid);
            return null;
        }

        for (Entity entity : world.getEntities()) {
            if (entity != null && uuid.equals(entity.getUuid())) {
                CACHE.put(uuid, new WeakReference<>(entity));
                return entity;
            }
        }

        CACHE.remove(uuid);
        return null;
    }

    public static void clear() {
        CACHE.clear();
    }
}

