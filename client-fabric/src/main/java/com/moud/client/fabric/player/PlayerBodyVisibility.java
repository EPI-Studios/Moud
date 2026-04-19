package com.moud.client.fabric.player;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerBodyVisibility {
    private static final Set<String> fullyHidden = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Set<String>> hiddenPartsByUuid = new ConcurrentHashMap<>();

    private PlayerBodyVisibility() {
    }

    public static void setBodyVisible(String uuid, boolean visible) {
        if (uuid == null || uuid.isBlank()) return;
        if (visible) fullyHidden.remove(uuid);
        else fullyHidden.add(uuid);
    }

    public static boolean isBodyVisible(String uuid) {
        return uuid == null || !fullyHidden.contains(uuid);
    }

    public static void setPartVisible(String uuid, String part, boolean visible) {
        if (uuid == null || uuid.isBlank() || part == null || part.isBlank()) return;
        String key = part.trim().toLowerCase();
        if (visible) {
            Set<String> parts = hiddenPartsByUuid.get(uuid);
            if (parts != null) parts.remove(key);
        } else {
            hiddenPartsByUuid.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    public static boolean isPartVisible(String uuid, String part) {
        if (uuid == null || part == null) return true;
        Set<String> parts = hiddenPartsByUuid.get(uuid);
        return parts == null || !parts.contains(part.trim().toLowerCase());
    }

    public static Set<String> hiddenParts(String uuid) {
        Set<String> parts = uuid == null ? null : hiddenPartsByUuid.get(uuid);
        return parts == null ? Set.of() : new HashSet<>(parts);
    }

    public static void clearPlayer(String uuid) {
        if (uuid == null) return;
        fullyHidden.remove(uuid);
        hiddenPartsByUuid.remove(uuid);
    }

    public static void clear() {
        fullyHidden.clear();
        hiddenPartsByUuid.clear();
    }
}
