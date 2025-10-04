package com.moud.server;

import com.moud.server.animation.AnimationManager;
import com.moud.server.api.HotReloadEndpoint;
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
import com.moud.server.zone.ZoneManager;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MoudEngine {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(MoudEngine.class);

    private JavaScriptRuntime runtime;
    private final AssetManager assetManager;
    private final AnimationManager animationManager;
    private final ClientScriptManager clientScriptManager;
    private final ServerNetworkManager networkManager;
    private final EventDispatcher eventDispatcher;
    private ScriptingAPI scriptingAPI;
    private final AsyncManager asyncManager;
    private final PacketEngine packetEngine;
    private final CursorService cursorService;
    private final HotReloadEndpoint hotReloadEndpoint;

    private final ZoneManager zoneManager;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private static MoudEngine instance;

    public static MoudEngine getInstance() {
        return instance;
    }

    public MoudEngine(String[] launchArgs) {
        instance = this;
        LOGGER.startup("Initializing Moud Engine...");

        boolean enableReload = hasArg(launchArgs, "--enable-reload");
        int port = getPortFromArgs(launchArgs);


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

            this.animationManager = new AnimationManager();
            animationManager.initialize();

            this.clientScriptManager = new ClientScriptManager();
            clientScriptManager.initialize();

            this.eventDispatcher = new EventDispatcher(this);
            this.runtime = new JavaScriptRuntime(this);
            this.asyncManager = new AsyncManager(this);

            this.networkManager = new ServerNetworkManager(eventDispatcher, clientScriptManager);
            networkManager.initialize();

            this.zoneManager = new ZoneManager(this);

            this.cursorService = CursorService.getInstance(networkManager);
            cursorService.initialize();

            this.scriptingAPI = new ScriptingAPI(this);
            bindGlobalAPIs();

            this.hotReloadEndpoint = new HotReloadEndpoint(this, enableReload);
            hotReloadEndpoint.start(port);

            loadUserScripts().thenRun(() -> {
                initialized.set(true);
                this.eventDispatcher.dispatchLoadEvent("server.load");
                LOGGER.startup("Moud Engine initialized successfully");
            }).exceptionally(ex -> {
                LOGGER.critical("Failed to load user scripts during startup. The server might not function correctly.", ex);
                return null;
            });


            LOGGER.startup("Moud Engine initialized successfully");
        } catch (Exception e) {
            LOGGER.critical("Failed to initialize Moud engine: {}", e.getMessage(), e);
            throw new RuntimeException("Engine initialization failed", e);
        }
    }

    private boolean hasArg(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private int getPortFromArgs(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid port number, using default 25565");
                    return 25565;
                }
            }
        }
        return 25565;
    }

    private void bindGlobalAPIs() {
        this.scriptingAPI = new ScriptingAPI(this);
        runtime.bindAPIs(scriptingAPI, new AssetProxy(assetManager), new ConsoleAPI());
    }

    public void reloadUserScripts() {
        LOGGER.info("Reloading user scripts...");
        CompletableFuture.runAsync(() -> {
            try {
                if (this.runtime != null) {
                    this.runtime.shutdown();
                }

                LOGGER.info("Initializing new scripting environment...");
                this.runtime = new JavaScriptRuntime(this);

                this.bindGlobalAPIs();

                Path entryPoint = ProjectLoader.findEntryPoint();
                if (!entryPoint.toFile().exists()) {
                    LOGGER.warn("No entry point found at: {}", entryPoint);
                    return;
                }
                this.runtime.executeScript(entryPoint).join();

                LOGGER.success("User scripts reloaded successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to reload user scripts", e);
            }
        });
    }

    private CompletableFuture<Void> loadUserScripts() {
        return CompletableFuture.runAsync(() -> {
            try {
                Path entryPoint = ProjectLoader.findEntryPoint();
                if (!entryPoint.toFile().exists()) {
                    LOGGER.warn("No entry point found at: {}", entryPoint);
                    return;
                }
                runtime.executeScript(entryPoint).join();
            } catch (Exception e) {
                LOGGER.error("Failed to find or load project script", e);
                throw new RuntimeException(e);
            }
        });
    }

    public void shutdown() {
        if (hotReloadEndpoint != null) hotReloadEndpoint.stop();

        if (cursorService != null) cursorService.shutdown();
        if (asyncManager != null) asyncManager.shutdown();
        if (runtime != null) runtime.shutdown();
        LOGGER.shutdown("Moud Engine shutdown complete.");
    }

    public ScriptingAPI getScriptingAPI() {
        return scriptingAPI;
    }

    public EventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }

    public JavaScriptRuntime getRuntime() {
        return runtime;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public AsyncManager getAsyncManager() {
        return asyncManager;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

}