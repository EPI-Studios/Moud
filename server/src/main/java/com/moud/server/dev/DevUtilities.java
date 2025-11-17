package com.moud.server.dev;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.MinecraftServer;

public final class DevUtilities {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            DevUtilities.class,
            LogContext.builder().put("subsystem", "dev-utils").build()
    );

    private static boolean enabled;
    private static boolean registered;

    private DevUtilities() {
    }

    public static void initialize(boolean enable) {
        enabled = enable;
        if (!enable) {
            LOGGER.info("Developer utilities disabled");
            return;
        }
        if (registered) {
            LOGGER.debug("Developer utilities already registered");
            return;
        }
        registered = true;
        LOGGER.info("Developer utilities enabled");
        registerCommands();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void registerCommands() {
        var commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new SharedValueInspectCommand());
        commandManager.register(new NetworkProbeCommand());
        commandManager.register(new GamemodeCommand());
        commandManager.register(new SpawnLightCommand());
        commandManager.register(new SpawnPhysicsCommand());
    }
}
