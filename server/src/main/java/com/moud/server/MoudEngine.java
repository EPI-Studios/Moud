package com.moud.server;

import com.moud.server.api.ScriptingAPI;
import com.moud.server.assets.AssetManager;
import com.moud.server.client.ClientScriptManager;
import com.moud.server.cursor.CursorService;
import com.moud.server.events.EventDispatcher;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.MinestomByteBuffer;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.project.ProjectLoader;
import com.moud.server.proxy.AssetProxy;
import com.moud.server.scripting.JavaScriptRuntime;
import com.moud.server.task.AsyncManager;
import com.moud.network.dispatcher.NetworkDispatcher;
import com.moud.network.engine.PacketEngine;
import com.moud.network.buffer.ByteBuffer;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MoudEngine {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(MoudEngine.class);

    private final JavaScriptRuntime runtime;
    private final AssetManager assetManager;
    private final ClientScriptManager clientScriptManager;
    private final ServerNetworkManager networkManager;
    private final EventDispatcher eventDispatcher;
    private final ScriptingAPI scriptingAPI;
    private final AsyncManager asyncManager;
    private final PacketEngine packetEngine;
    private final CursorService cursorService;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public MoudEngine(String[] launchArgs) {
        LOGGER.startup("Initializing Moud Engine...");

        try {
            Path projectRoot = ProjectLoader.resolveProjectRoot(launchArgs)
                    .orElseThrow(() -> new IllegalStateException("Could not find a valid Moud project root."));

            LOGGER.info("Loading project from: {}", projectRoot);

            this.packetEngine = new PacketEngine();
            packetEngine.initialize("com.moud.network");

            NetworkDispatcher.ByteBufferFactory bufferFactory = new NetworkDispatcher.ByteBufferFactory() {
                @Override
                public ByteBuffer create() {
                    return new MinestomByteBuffer();
                }

                @Override
                public ByteBuffer wrap(byte[] data) {
                    return new MinestomByteBuffer(data);
                }
            };
            NetworkDispatcher dispatcher = packetEngine.createDispatcher(bufferFactory);

            this.assetManager = new AssetManager(projectRoot);
            assetManager.initialize();

            this.clientScriptManager = new ClientScriptManager();
            clientScriptManager.initialize();

            this.eventDispatcher = new EventDispatcher(this);
            this.runtime = new JavaScriptRuntime(this);
            this.asyncManager = new AsyncManager(this);

            this.networkManager = new ServerNetworkManager(eventDispatcher, clientScriptManager);
            networkManager.initialize();

            this.cursorService = CursorService.getInstance(networkManager);
            cursorService.initialize();

            this.scriptingAPI = new ScriptingAPI(this);
            bindGlobalAPIs();

            loadUserScripts();

            initialized.set(true);
            LOGGER.startup("Moud Engine initialized successfully");
        } catch (Exception e) {
            LOGGER.critical("Failed to initialize Moud engine: {}", e.getMessage(), e);
            throw new RuntimeException("Engine initialization failed", e);
        }
    }

    private void bindGlobalAPIs() {
        runtime.bindGlobal("api", scriptingAPI);
        runtime.bindGlobal("assets", new AssetProxy(assetManager));

        ConsoleAPI consoleImpl = new ConsoleAPI();
        runtime.bindConsole(consoleImpl);
    }

    private void loadUserScripts() {
        CompletableFuture.runAsync(() -> {
            try {
                Path entryPoint = ProjectLoader.findEntryPoint();
                if (!entryPoint.toFile().exists()) {
                    LOGGER.warn("No entry point found at: {}", entryPoint);
                    return;
                }
                runtime.executeScript(entryPoint);
            } catch (Exception e) {
                LOGGER.error("Failed to find or load project script", e);
            }
        });
    }

    public void shutdown() {
        if (cursorService != null) cursorService.shutdown();
        if (asyncManager != null) asyncManager.shutdown();
        if (runtime != null) runtime.shutdown();
        LOGGER.shutdown("Moud Engine shutdown complete.");
    }

    public EventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }

    public JavaScriptRuntime getRuntime() {
        return runtime;
    }



    public AsyncManager getAsyncManager() {
        return asyncManager;
    }
}