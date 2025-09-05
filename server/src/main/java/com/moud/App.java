package com.moud;

import com.moud.server.MoudEngine;
import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        try {
            MoudEngine moudEngine = new MoudEngine();

            Runtime.getRuntime().addShutdownHook(new Thread(moudEngine::shutdown));

            minecraftServer.start("0.0.0.0", 25565);
            LOGGER.info("Server started on port 25565");
        } catch (Exception e) {
            LOGGER.error("Failed to start server", e);
            System.exit(1);
        }
    }
}