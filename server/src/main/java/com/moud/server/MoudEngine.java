package com.moud.server;

import com.moud.server.api.ScriptingAPI;
import com.moud.server.assets.AssetManager;
import com.moud.server.client.ClientScriptManager;
import com.moud.server.events.EventDispatcher;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.network.ServerPacketHandler;
import com.moud.server.project.ProjectLoader;
import com.moud.server.proxy.AssetProxy;
import com.moud.server.scripting.JavaScriptRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class MoudEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoudEngine.class);

    private final JavaScriptRuntime runtime;
    private final AssetManager assetManager;
    private final ClientScriptManager clientScriptManager;
    private final ServerNetworkManager networkManager;

    public MoudEngine() {
        try {
            Path projectRoot = ProjectLoader.findProjectRoot();

            // --- LA MODIFICATION EST ICI ---
            // 1. Initialiser l'AssetManager (votre code existant était correct)
            this.assetManager = new AssetManager(projectRoot);
            assetManager.initialize();

            this.clientScriptManager = new ClientScriptManager();
            clientScriptManager.initialize();

            EventDispatcher eventDispatcher = new EventDispatcher();
            this.runtime = new JavaScriptRuntime();
            ScriptingAPI scriptingAPI = new ScriptingAPI(eventDispatcher);
            AssetProxy assetProxy = new AssetProxy(assetManager);

            ServerPacketHandler packetHandler = new ServerPacketHandler(eventDispatcher);
            this.networkManager = new ServerNetworkManager(packetHandler, clientScriptManager);
            networkManager.initialize();

            runtime.bindGlobal("api", scriptingAPI);
            runtime.bindGlobal("assets", assetProxy);
            runtime.bindGlobal("console", new ConsoleAPI());

            loadUserScript();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Moud engine", e);
            throw new RuntimeException(e);
        }
    }

    private void loadUserScript() {
        try {
            Path entryPoint = ProjectLoader.findEntryPoint();

            if (!entryPoint.toFile().exists()) {
                LOGGER.warn("No entry point found at: {}", entryPoint);
                return;
            }

            runtime.executeScript(entryPoint)
                    .thenAccept(result -> LOGGER.info("Script loaded successfully"))
                    .exceptionally(throwable -> {
                        LOGGER.error("Failed to load script", throwable);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to find project", e);
        }
    }

    public void shutdown() {
        runtime.shutdown();
    }

    public static class ConsoleAPI {
        private static final Logger CONSOLE_LOGGER = LoggerFactory.getLogger("Script.Console");

        public void log(Object... args) {
            CONSOLE_LOGGER.info(formatArgs(args));
        }

        public void warn(Object... args) {
            CONSOLE_LOGGER.warn(formatArgs(args));
        }

        public void error(Object... args) {
            CONSOLE_LOGGER.error(formatArgs(args));
        }

        private String formatArgs(Object[] args) {
            if (args.length == 0) return "";
            if (args.length == 1) return String.valueOf(args[0]);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(args[i]);
            }
            return sb.toString();
        }
    }
}