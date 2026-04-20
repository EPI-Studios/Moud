package com.moud.server.minestom.scripting.api.modules;

import com.moud.server.minestom.persistence.PersistenceService;
import java.util.UUID;
import org.graalvm.polyglot.HostAccess;

public final class PersistApi {

    private final PersistenceService service;

    public PersistApi(PersistenceService service) {
        this.service = service;
    }

    @HostAccess.Export
    public String get(String playerUuid, String key) {
        if (service == null) return "";
        UUID uuid = parseUuid(playerUuid);
        if (uuid == null) return "";
        String v = service.getPlayer(uuid, key);
        return v == null ? "" : v;
    }

    @HostAccess.Export
    public void set(String playerUuid, String key, String value) {
        if (service == null) return;
        UUID uuid = parseUuid(playerUuid);
        if (uuid == null) return;
        service.setPlayer(uuid, key, value);
    }

    @HostAccess.Export
    public void save(String playerUuid) {
        if (service == null) return;
        UUID uuid = parseUuid(playerUuid);
        if (uuid == null) return;
        service.savePlayer(uuid);
    }

    @HostAccess.Export
    public String getWorld(String key) {
        if (service == null) return "";
        String v = service.getWorld(key);
        return v == null ? "" : v;
    }

    @HostAccess.Export
    public void setWorld(String key, String value) {
        if (service == null) return;
        service.setWorld(key, value);
    }

    @HostAccess.Export
    public void saveWorld() {
        if (service == null) return;
        service.saveWorld();
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
