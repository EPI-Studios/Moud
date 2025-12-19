package com.moud.server.console;

import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import net.minestom.server.MinecraftServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ServerConsole {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ServerConsole.class,
            LogContext.builder().put("subsystem", "console").build()
    );
    private static volatile boolean started;

    private ServerConsole() {
    }

    public static synchronized void startAsync() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(ServerConsole::runLoop, "moud-console");
        thread.setDaemon(true);
        thread.start();
        LOGGER.info("Console ready");
    }

    private static void runLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String command = line.trim();
                if (command.isEmpty()) {
                    continue;
                }
                try {
                    MinecraftServer.getCommandManager().execute(
                            MinecraftServer.getCommandManager().getConsoleSender(),
                            command
                    );
                } catch (Exception e) {
                    LOGGER.error("Failed to execute console command '{}'", command, e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Console loop failed", e);
        }
        LOGGER.info("Console input closed");
    }
}

