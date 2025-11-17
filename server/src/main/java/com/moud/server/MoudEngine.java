package com.moud.server;

import com.moud.server.animation.AnimationManager;
import com.moud.server.api.HotReloadEndpoint;
import com.moud.server.api.ScriptingAPI;
import com.moud.server.assets.AssetManager;
import com.moud.server.client.ClientScriptManager;
import com.moud.server.cursor.CursorService;
import com.moud.server.dev.DevUtilities;
import com.moud.server.editor.SceneManager;
import com.moud.server.events.EventDispatcher;
import com.moud.server.instance.InstanceManager;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.MinestomByteBuffer;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.project.ProjectLoader;
import com.moud.server.plugin.PluginManager;
import com.moud.server.proxy.AssetProxy;
import com.moud.server.profiler.ProfilerService;
import com.moud.server.profiler.ProfilerUI;
import com.moud.server.physics.PhysicsService;
import com.moud.server.scripting.JavaScriptRuntime;
import com.moud.server.task.AsyncManager;
import com.moud.network.dispatcher.NetworkDispatcher;
import com.moud.network.engine.PacketEngine;
import com.moud.network.buffer.ByteBuffer;
import com.moud.server.shared.SharedValueManager;
import com.moud.server.zone.ZoneManager;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MoudEngine {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            MoudEngine.class,
            LogContext.builder().put("subsystem", "engine").build()
    );

    private JavaScriptRuntime runtime;
    private final AssetManager assetManager;
    private final AnimationManager animationManager;
    private final ClientScriptManager clientScriptManager;
    private final com.moud.server.plugin.PluginManager pluginManager;
    private final ServerNetworkManager networkManager;
    private final EventDispatcher eventDispatcher;
    private ScriptingAPI scriptingAPI;
    private final AsyncManager asyncManager;
    private final PacketEngine packetEngine;
    private final CursorService cursorService;
    private final HotReloadEndpoint hotReloadEndpoint;
    private final ZoneManager zoneManager;
    private final PhysicsService physicsService;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean reloading = new AtomicBoolean(false);
    private final AtomicReference<ReloadState> reloadState = new AtomicReference<>(ReloadState.IDLE);

    private static MoudEngine instance;

    public static MoudEngine getInstance() {
        return instance;
    }

    public MoudEngine(String[] launchArgs) {
        instance = this;
        LOGGER.startup("Initializing Moud Engine...");

        boolean enableReload = hasArg(launchArgs, "--enable-reload");
        boolean enableDevUtilities = hasArg(launchArgs, "--dev-utils");
        boolean enableProfileUi = hasArg(launchArgs, "--profile-ui");
        int port = getPortFromArgs(launchArgs);

        try {
            Path projectRoot = ProjectLoader.resolveProjectRoot(launchArgs)
                    .orElseThrow(() -> new IllegalStateException("Could not find a valid Moud project root."));

            LOGGER.info(LogContext.builder()
                    .put("project_root", projectRoot.toString())
                    .build(), "Loading project from: {}", projectRoot);

            SceneManager.getInstance().initialize(projectRoot);

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
            SceneManager.getInstance().setAssetManager(assetManager);

            this.animationManager = new AnimationManager();
            animationManager.initialize();

            this.clientScriptManager = new ClientScriptManager();
            clientScriptManager.initialize();

            this.pluginManager = new com.moud.server.plugin.PluginManager(projectRoot);

            this.eventDispatcher = new EventDispatcher(this);
            this.runtime = new JavaScriptRuntime(this);
            this.asyncManager = new AsyncManager(this);

            InstanceManager.getInstance().initialize();

            this.networkManager = new ServerNetworkManager(eventDispatcher, clientScriptManager);
            networkManager.initialize();

            this.zoneManager = new ZoneManager(this);

            this.physicsService = PhysicsService.getInstance();
            physicsService.initialize();

            this.cursorService = CursorService.getInstance(networkManager);
            cursorService.initialize();

            SharedValueManager.getInstance().initialize();

            this.scriptingAPI = new ScriptingAPI(this);
            bindGlobalAPIs();

            this.hotReloadEndpoint = new HotReloadEndpoint(this, enableReload);
            hotReloadEndpoint.start(port);

            DevUtilities.initialize(enableDevUtilities);
            ProfilerService.getInstance().start();
            if (enableProfileUi) {
                LOGGER.info(LogContext.builder().put("profile_ui", true).build(),
                        "Profiler UI flag detected; launching window");
                ProfilerUI.launchAsync();
            }

            loadUserScripts().thenRun(() -> {
                pluginManager.loadPlugins();
                initialized.set(true);
                this.eventDispatcher.dispatchLoadEvent("server.load");
                LOGGER.startup("Moud Engine initialized successfully");
            }).exceptionally(ex -> {
                LOGGER.critical("Failed to load user scripts during startup. The server might not function correctly.", ex);
                return null;
            });

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
                String rawPort = args[i + 1];
                try {
                    return Integer.parseInt(rawPort);
                } catch (NumberFormatException e) {
                    LOGGER.warn(LogContext.builder()
                            .put("raw_port", rawPort)
                            .build(), "Invalid port number, using default 25565");
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
        reloadUserScripts(null);
    }

    public void reloadUserScripts(ReloadBundle bundle) {
        if (!reloading.compareAndSet(false, true)) {
            LOGGER.warn("Reload already in progress, ignoring request");
            return;
        }

        LOGGER.info("Starting async reload of user scripts...");
        reloadState.set(ReloadState.SHUTTING_DOWN_OLD);

        CompletableFuture.runAsync(() -> {
            try {
                if (bundle != null && bundle.clientBundle() != null) {
                    clientScriptManager.updateClientBundle(bundle.clientBundle(), bundle.hash());
                } else {
                    try {
                        clientScriptManager.initialize();
                    } catch (Exception e) {
                        LOGGER.warn("Failed to rebuild client bundle during reload", e);
                    }
                }

                if (assetManager != null) {
                    try {
                        assetManager.refresh();
                    } catch (Exception e) {
                        LOGGER.warn("Failed to refresh asset catalog", e);
                    }
                }

                if (this.runtime != null) {
                    LOGGER.info("Shutting down old runtime...");
                    this.runtime.shutdown();
                    reloadState.set(ReloadState.INITIALIZING_NEW);
                }

                LOGGER.info("Initializing new scripting environment...");
                this.runtime = new JavaScriptRuntime(this);
                this.bindGlobalAPIs();
                reloadState.set(ReloadState.LOADING_SCRIPTS);

                if (bundle != null && bundle.serverBundle() != null) {
                    this.runtime.executeSource(bundle.serverBundle(), "moud-server.js").join();
                } else {
                    Path entryPoint = ProjectLoader.findEntryPoint();
                    if (!Files.exists(entryPoint)) {
                        LOGGER.warn(LogContext.builder()
                                .put("entry_point", entryPoint.toString())
                                .build(), "No entry point found at: {}", entryPoint);
                        reloadState.set(ReloadState.FAILED);
                        return;
                    }

                    this.runtime.executeScript(entryPoint).join();
                }
                reloadState.set(ReloadState.COMPLETE);
                LOGGER.success("User scripts reloaded successfully");

            } catch (Exception e) {
                LOGGER.error(LogContext.builder()
                        .put("phase", "reload")
                        .build(), "Failed to reload user scripts", e);
                reloadState.set(ReloadState.FAILED);
            } finally {
                reloading.set(false);
            }
        });
    }

    private CompletableFuture<Void> loadUserScripts() {
        return CompletableFuture.runAsync(() -> {
            try {
                Path entryPoint = ProjectLoader.findEntryPoint();
                if (!entryPoint.toFile().exists()) {
                    LOGGER.warn(LogContext.builder()
                            .put("entry_point", entryPoint.toString())
                            .build(), "No entry point found at: {}", entryPoint);
                    return;
                }
                runtime.executeScript(entryPoint).join();
            } catch (Exception e) {
                LOGGER.error(LogContext.builder()
                        .put("phase", "initial-load")
                        .build(), "Failed to find or load project script", e);
                throw new RuntimeException(e);
            }
        });
    }

    public void shutdown() {
        if (hotReloadEndpoint != null) hotReloadEndpoint.stop();
        if (cursorService != null) cursorService.shutdown();
        if (asyncManager != null) asyncManager.shutdown();
        if (runtime != null) runtime.shutdown();
        if (physicsService != null) physicsService.shutdown();
        if (pluginManager != null) pluginManager.shutdown();
        SharedValueManager.getInstance().shutdown();
        ProfilerService.getInstance().stop();
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

    public ReloadState getReloadState() {
        return reloadState.get();
    }

    public boolean isReloading() {
        return reloading.get();
    }

    public enum ReloadState {
        IDLE,
        SHUTTING_DOWN_OLD,
        INITIALIZING_NEW,
        LOADING_SCRIPTS,
        COMPLETE,
        FAILED
    }

    public record ReloadBundle(String hash, String serverBundle, byte[] clientBundle) {
    }
}
