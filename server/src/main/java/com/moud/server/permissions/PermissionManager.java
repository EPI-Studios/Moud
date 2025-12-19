package com.moud.server.permissions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PermissionManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            PermissionManager.class,
            LogContext.builder().put("subsystem", "permissions").build()
    );
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final int SCHEMA_VERSION = 1;
    private static final String DIRECTORY_NAME = ".moud";
    private static final String FILE_NAME = "permissions.json";

    private static PermissionManager instance;

    private final ConcurrentMap<UUID, EnumSet<ServerPermission>> permissionsByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> lastKnownNameByPlayer = new ConcurrentHashMap<>();
    private volatile Path storagePath;
    private volatile boolean initialized;

    public static PermissionManager getInstance() {
        if (instance == null) {
            instance = new PermissionManager();
        }
        return instance;
    }

    private PermissionManager() {
    }

    public synchronized void initialize(Path projectRoot) {
        Path root = projectRoot != null ? projectRoot : Path.of(".");
        Path desired = root.resolve(DIRECTORY_NAME).resolve(FILE_NAME);
        if (initialized && desired.equals(this.storagePath)) {
            return;
        }
        this.storagePath = desired;
        reload();
        initialized = true;
    }

    public Path getStoragePath() {
        Path path = storagePath;
        if (path == null) {
            return Path.of(DIRECTORY_NAME).resolve(FILE_NAME);
        }
        return path;
    }

    public boolean has(Player player, ServerPermission permission) {
        if (player == null || permission == null) {
            return false;
        }
        return has(player.getUuid(), permission);
    }

    public boolean has(UUID playerId, ServerPermission permission) {
        if (playerId == null || permission == null) {
            return false;
        }
        EnumSet<ServerPermission> perms = permissionsByPlayer.get(playerId);
        if (perms == null || perms.isEmpty()) {
            return false;
        }
        if (perms.contains(ServerPermission.OP)) {
            return true;
        }
        return perms.contains(permission);
    }

    public EnumSet<ServerPermission> getDirectPermissions(UUID playerId) {
        if (playerId == null) {
            return EnumSet.noneOf(ServerPermission.class);
        }
        EnumSet<ServerPermission> perms = permissionsByPlayer.get(playerId);
        if (perms == null || perms.isEmpty()) {
            return EnumSet.noneOf(ServerPermission.class);
        }
        return EnumSet.copyOf(perms);
    }

    public Map<UUID, EnumSet<ServerPermission>> snapshot() {
        return permissionsByPlayer.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> EnumSet.copyOf(entry.getValue())));
    }

    public String getLastKnownName(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return lastKnownNameByPlayer.get(playerId);
    }

    public synchronized boolean grant(UUID playerId, ServerPermission permission, String lastKnownName) {
        ensureStoragePath();
        if (playerId == null || permission == null) {
            return false;
        }
        EnumSet<ServerPermission> updated = permissionsByPlayer.compute(playerId, (id, existing) -> {
            EnumSet<ServerPermission> next = existing == null ? EnumSet.noneOf(ServerPermission.class) : EnumSet.copyOf(existing);
            next.add(permission);
            return next;
        });
        if (lastKnownName != null && !lastKnownName.isBlank()) {
            lastKnownNameByPlayer.put(playerId, lastKnownName);
        }
        save();
        return updated.contains(permission);
    }

    public synchronized boolean revoke(UUID playerId, ServerPermission permission) {
        ensureStoragePath();
        if (playerId == null || permission == null) {
            return false;
        }
        EnumSet<ServerPermission> updated = permissionsByPlayer.computeIfPresent(playerId, (id, existing) -> {
            EnumSet<ServerPermission> next = EnumSet.copyOf(existing);
            next.remove(permission);
            return next.isEmpty() ? null : next;
        });
        if (updated == null) {
            return true;
        }
        save();
        return !updated.contains(permission);
    }

    public synchronized void reload() {
        ensureStoragePath();
        permissionsByPlayer.clear();
        lastKnownNameByPlayer.clear();
        Path path = storagePath;
        if (path == null || !Files.exists(path)) {
            LOGGER.info("No permissions file found at {}", getStoragePath());
            return;
        }
        try {
            PermissionsFile file = MAPPER.readValue(path.toFile(), PermissionsFile.class);
            if (file == null || file.players == null || file.players.isEmpty()) {
                LOGGER.info("Permissions file {} is empty", path);
                return;
            }
            if (file.schemaVersion != SCHEMA_VERSION) {
                LOGGER.warn("Unsupported permissions file schema {} (expected {})", file.schemaVersion, SCHEMA_VERSION);
            }
            file.players.forEach((rawId, entry) -> {
                UUID uuid;
                try {
                    uuid = UUID.fromString(rawId);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Skipping invalid UUID key in permissions file: {}", rawId);
                    return;
                }
                EnumSet<ServerPermission> perms = parsePermissions(entry != null ? entry.permissions : null);
                if (!perms.isEmpty()) {
                    permissionsByPlayer.put(uuid, perms);
                }
                if (entry != null && entry.name != null && !entry.name.isBlank()) {
                    lastKnownNameByPlayer.put(uuid, entry.name);
                }
            });
            LOGGER.info("Loaded {} permission entries from {}", permissionsByPlayer.size(), path);
        } catch (Exception e) {
            LOGGER.error("Failed to load permissions from {}", path, e);
        }
    }

    public synchronized void save() {
        ensureStoragePath();
        Path path = storagePath;
        if (path == null) {
            LOGGER.error("Permission storage path not configured");
            return;
        }
        PermissionsFile file = new PermissionsFile();
        file.schemaVersion = SCHEMA_VERSION;
        file.players = new HashMap<>();
        permissionsByPlayer.forEach((uuid, perms) -> {
            PlayerPermissions entry = new PlayerPermissions();
            entry.name = lastKnownNameByPlayer.get(uuid);
            entry.permissions = perms.stream().map(Enum::name).toList();
            file.players.put(uuid.toString(), entry);
        });
        try {
            Files.createDirectories(path.getParent());
            String json = MAPPER.writeValueAsString(file);
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to write permissions to {}", path, e);
        }
    }

    private EnumSet<ServerPermission> parsePermissions(List<String> names) {
        EnumSet<ServerPermission> set = EnumSet.noneOf(ServerPermission.class);
        if (names == null || names.isEmpty()) {
            return set;
        }
        for (String raw : names) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                set.add(ServerPermission.valueOf(raw.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown permission '{}' in {}", raw, getStoragePath());
            }
        }
        return set;
    }

    private void ensureStoragePath() {
        if (storagePath != null) {
            return;
        }
        storagePath = Path.of(DIRECTORY_NAME).resolve(FILE_NAME);
    }

    private static final class PermissionsFile {
        public int schemaVersion = SCHEMA_VERSION;
        public Map<String, PlayerPermissions> players = new HashMap<>();
    }

    private static final class PlayerPermissions {
        public String name;
        public List<String> permissions = List.of();
    }
}
