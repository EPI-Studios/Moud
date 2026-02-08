package com.moud.server.permissions;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.MinecraftServer;

public final class PermissionCommands {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            PermissionCommands.class,
            LogContext.builder().put("subsystem", "permissions").build()
    );
    private static boolean registered;

    private PermissionCommands() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        var commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new OpCommand());
        commandManager.register(new DeopCommand());
        commandManager.register(new MoudPermCommand());
        LOGGER.info("Registered permission commands");
    }
}

