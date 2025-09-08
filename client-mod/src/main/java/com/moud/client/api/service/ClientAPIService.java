package com.moud.client.api.service;

import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.client.update.ClientUpdateManager;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientAPIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAPIService.class);
    public static ClientAPIService INSTANCE;

    public final NetworkService network;
    public final RenderingService rendering;
    public final UIService ui;
    public final ConsoleAPI console;
    public final CameraService camera;
    private final ClientUpdateManager updateManager;

    public InputService input;

    private ClientScriptingRuntime scriptingRuntime;

    public ClientAPIService() {
        if (INSTANCE != null) {
            LOGGER.warn("ClientAPIService is being instantiated more than once. This may lead to unexpected behavior.");
        }
        INSTANCE = this;

        this.network = new NetworkService();
        this.rendering = new RenderingService();
        this.ui = new UIService();
        this.console = new ConsoleAPI();
        this.camera = new CameraService();

        this.updateManager = new ClientUpdateManager(this);

        LOGGER.info("ClientAPIService partially initialized. Waiting for scripting runtime...");
    }

    public void setRuntime(ClientScriptingRuntime runtime) {
        if (this.scriptingRuntime != null) {
            LOGGER.warn("Scripting runtime is being set more than once.");
            return;
        }
        this.scriptingRuntime = runtime;


        this.input = new InputService(runtime);

        LOGGER.info("InputService initialized.");

        if (runtime.getContext() != null) {
            updateScriptingContext(runtime.getContext());
        } else {
            LOGGER.warn("Runtime was set, but its GraalVM context is null.");
        }
    }

    public void updateScriptingContext(Context context) {
        this.network.setContext(context);
        this.rendering.setContext(context);
        this.ui.setContext(context);
        this.console.setContext(context);
        this.camera.setContext(context);

        if (this.input != null) {
            this.input.setContext(context);
        } else {
            LOGGER.warn("Attempted to update script context before InputService was initialized.");
        }

        LOGGER.debug("ClientAPIService updated scripting context for all child services.");
    }

    public void cleanup() {
        if (network != null) network.cleanUp();
        if (rendering != null) rendering.cleanUp();
        if (ui != null) ui.cleanUp();
        if (console != null) console.cleanUp();
        if (camera != null) camera.cleanUp();
        if (input != null) input.cleanUp();
        if (updateManager != null) updateManager.cleanup();

        this.scriptingRuntime = null; // Release reference
        LOGGER.info("ClientAPIService cleaned up all child services.");
    }

    public ClientUpdateManager getUpdateManager() {
        return updateManager;
    }
}