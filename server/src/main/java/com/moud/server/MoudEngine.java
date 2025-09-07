package com.moud.server;

import com.moud.server.api.ScriptingAPI;
import com.moud.server.api.exception.APIException;
import com.moud.server.assets.AssetManager;
import com.moud.server.client.ClientScriptManager;
import com.moud.server.events.EventDispatcher;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.network.ServerPacketHandler;
import com.moud.server.project.ProjectLoader;
import com.moud.server.proxy.AssetProxy;
import com.moud.server.scripting.JavaScriptRuntime;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MoudEngine {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(MoudEngine.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final JavaScriptRuntime runtime;
    private final AssetManager assetManager;
    private final ClientScriptManager clientScriptManager;
    private final ServerNetworkManager networkManager;
    private final EventDispatcher eventDispatcher;
    private final ScriptingAPI scriptingAPI;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);

    public MoudEngine() {
        LOGGER.startup("Initializing Moud Engine...");

        try {
            Path projectRoot = initializeProject();

            this.assetManager = initializeAssetManager(projectRoot);
            this.clientScriptManager = initializeClientScriptManager();
            this.eventDispatcher = new EventDispatcher();
            this.runtime = new JavaScriptRuntime();

            this.scriptingAPI = new ScriptingAPI(eventDispatcher);
            bindGlobalAPIs();

            this.networkManager = initializeNetworking();

            loadUserScripts();

            initialized.set(true);
            LOGGER.startup("Moud Engine initialized successfully");

        } catch (Exception e) {
            LOGGER.critical("Failed to initialize Moud engine: {}", e.getMessage(), e);
            throw new RuntimeException("Engine initialization failed", e);
        }
    }

    private Path initializeProject() throws Exception {
        try {
            Path projectRoot = ProjectLoader.findProjectRoot();
            LOGGER.info("Project root found: {}", projectRoot);
            return projectRoot;
        } catch (Exception e) {
            throw new APIException("PROJECT_INIT_FAILED", "Failed to initialize project", e);
        }
    }

    private AssetManager initializeAssetManager(Path projectRoot) throws Exception {
        try {
            AssetManager assetManager = new AssetManager(projectRoot);
            assetManager.initialize();
            LOGGER.success("Asset manager initialized");
            return assetManager;
        } catch (Exception e) {
            throw new APIException("ASSET_MANAGER_INIT_FAILED", "Failed to initialize asset manager", e);
        }
    }

    private ClientScriptManager initializeClientScriptManager() throws Exception {
        try {
            ClientScriptManager clientScriptManager = new ClientScriptManager();
            clientScriptManager.initialize();
            LOGGER.success("Client script manager initialized");
            return clientScriptManager;
        } catch (Exception e) {
            throw new APIException("CLIENT_SCRIPT_MANAGER_INIT_FAILED", "Failed to initialize client script manager", e);
        }
    }

    private void bindGlobalAPIs() {
        try {
            AssetProxy assetProxy = new AssetProxy(assetManager);
            ConsoleAPI consoleAPI = new ConsoleAPI();

            runtime.bindGlobal("api", scriptingAPI);
            runtime.bindGlobal("assets", assetProxy);
            runtime.bindGlobal("console", consoleAPI);

            LOGGER.success("Global APIs bound successfully");
        } catch (Exception e) {
            throw new APIException("API_BINDING_FAILED", "Failed to bind global APIs", e);
        }
    }

    private ServerNetworkManager initializeNetworking() throws Exception {
        try {
            ServerPacketHandler packetHandler = new ServerPacketHandler(eventDispatcher);
            ServerNetworkManager networkManager = new ServerNetworkManager(packetHandler, clientScriptManager);
            networkManager.initialize();
            LOGGER.success("Network manager initialized");
            return networkManager;
        } catch (Exception e) {
            throw new APIException("NETWORK_INIT_FAILED", "Failed to initialize networking", e);
        }
    }

    private void loadUserScripts() {
        CompletableFuture.runAsync(() -> {
            try {
                Path entryPoint = ProjectLoader.findEntryPoint();

                if (!entryPoint.toFile().exists()) {
                    LOGGER.warn("No entry point found at: {}", entryPoint);
                    return;
                }

                LOGGER.info("Loading user script: {}", entryPoint.getFileName());

                runtime.executeScript(entryPoint)
                        .thenAccept(result -> {
                            LOGGER.success("User script loaded successfully");
                        })
                        .exceptionally(throwable -> {
                            if (throwable.getCause() instanceof APIException apiException) {
                                LOGGER.scriptError("Script loading failed [{}]: {}",
                                        apiException.getErrorCode(), apiException.getMessage());
                            } else {
                                LOGGER.scriptError("Script loading failed: {}", throwable.getMessage(), throwable);
                            }
                            return null;
                        });

            } catch (Exception e) {
                LOGGER.error("Failed to find or load project script", e);
            }
        });
    }

    public void shutdown() {
        if (!shutdownInitiated.compareAndSet(false, true)) {
            LOGGER.warn("Shutdown already initiated");
            return;
        }

        LOGGER.shutdown("Initiating Moud Engine shutdown...");

        try {
            CompletableFuture<Void> shutdownFuture = CompletableFuture.runAsync(() -> {
                try {
                    if (scriptingAPI != null) {
                        scriptingAPI.shutdown();
                        LOGGER.debug("Scripting API shutdown completed");
                    }
                    if (runtime != null) {
                        runtime.shutdown();
                        LOGGER.debug("JavaScript runtime shutdown completed");
                    }
                    if (clientScriptManager != null) {
                        LOGGER.debug("Client script manager cleanup completed");
                    }
                    if (assetManager != null) {
                        LOGGER.debug("Asset manager cleanup completed");
                    }

                    LOGGER.shutdown("All systems shutdown successfully");

                } catch (Exception e) {
                    LOGGER.error("Error during shutdown", e);
                }
            });
            try {
                shutdownFuture.get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warn("Shutdown timeout exceeded, forcing shutdown");
            }

        } catch (Exception e) {
            LOGGER.error("Critical error during shutdown", e);
        } finally {
            initialized.set(false);
            LOGGER.shutdown("Moud Engine shutdown completed");
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public boolean isShuttingDown() {
        return shutdownInitiated.get();
    }

    public ScriptingAPI getScriptingAPI() {
        if (!isInitialized()) {
            throw new APIException("ENGINE_NOT_INITIALIZED", "Engine not initialized");
        }
        return scriptingAPI;
    }

    public EventDispatcher getEventDispatcher() {
        if (!isInitialized()) {
            throw new APIException("ENGINE_NOT_INITIALIZED", "Engine not initialized");
        }
        return eventDispatcher;
    }

    public AssetManager getAssetManager() {
        if (!isInitialized()) {
            throw new APIException("ENGINE_NOT_INITIALIZED", "Engine not initialized");
        }
        return assetManager;
    }

    public JavaScriptRuntime getRuntime() {
        if (!isInitialized()) {
            throw new APIException("ENGINE_NOT_INITIALIZED", "Engine not initialized");
        }
        return runtime;
    }

    public ServerNetworkManager getNetworkManager() {
        if (!isInitialized()) {
            throw new APIException("ENGINE_NOT_INITIALIZED", "Engine not initialized");
        }
        return networkManager;
    }
}