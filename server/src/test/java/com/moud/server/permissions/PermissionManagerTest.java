package com.moud.server.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionManagerTest {

    @Test
    void savesAndReloadsPermissions(@TempDir Path tempDir) throws Exception {
        PermissionManager manager = PermissionManager.getInstance();
        manager.initialize(tempDir);

        UUID playerId = UUID.randomUUID();
        manager.grant(playerId, ServerPermission.EDITOR, "Alice");
        manager.grant(playerId, ServerPermission.DEV_UTILS, "Alice");

        Path file = manager.getStoragePath();
        assertTrue(Files.exists(file), "permissions.json should be created");

        manager.reload();

        assertTrue(manager.has(playerId, ServerPermission.EDITOR));
        assertTrue(manager.has(playerId, ServerPermission.DEV_UTILS));
        assertEquals("Alice", manager.getLastKnownName(playerId));
    }

    @Test
    void opImpliesAllPermissions(@TempDir Path tempDir) {
        PermissionManager manager = PermissionManager.getInstance();
        manager.initialize(tempDir);

        UUID playerId = UUID.randomUUID();
        manager.grant(playerId, ServerPermission.OP, "Bob");

        assertTrue(manager.has(playerId, ServerPermission.OP));
        assertTrue(manager.has(playerId, ServerPermission.EDITOR));
        assertTrue(manager.has(playerId, ServerPermission.DEV_UTILS));

        EnumSet<ServerPermission> direct = manager.getDirectPermissions(playerId);
        assertEquals(EnumSet.of(ServerPermission.OP), direct);
        assertFalse(direct.contains(ServerPermission.EDITOR));
        assertFalse(direct.contains(ServerPermission.DEV_UTILS));
    }
}

