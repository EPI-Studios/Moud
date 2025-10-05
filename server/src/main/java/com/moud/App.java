package com.moud;

import com.moud.server.MoudEngine;
import net.minestom.server.MinecraftServer;
import net.minestom.server.extras.MojangAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        int port = 25565;
        boolean onlineMode = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[i + 1]);
                            i++;
                        } catch (NumberFormatException e) {
                            LOGGER.error("Invalid port number provided. Using default 25565.", e);
                        }
                    }
                    break;
                case "--online-mode":
                    if (i + 1 < args.length) {
                        onlineMode = Boolean.parseBoolean(args[i + 1]);
                        i++;
                    }
                    break;
            }
        }
        if (onlineMode) {
            MojangAuth.init();
            LOGGER.info("Mojang authentication is ENABLED.");
        } else {
            LOGGER.warn("Mojang authentication is DISABLED. Players can join with any username.");
        }

        MinecraftServer minecraftServer = MinecraftServer.init();

        try {
            MoudEngine moudEngine = new MoudEngine(args);

            Runtime.getRuntime().addShutdownHook(new Thread(moudEngine::shutdown));

            minecraftServer.start("0.0.0.0", port);
            LOGGER.info("Server started on port {}", port);

        } catch (Exception e) {
            LOGGER.error("Failed to start server", e);
            System.exit(1);
        }
    }
}