package com.moud.client.api.service;

import com.moud.client.runtime.ClientScriptingRuntime;
import com.moud.client.update.ClientUpdateManager;
import com.moud.client.shared.api.ClientSharedApiProxy;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;

public final class ClientAPIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAPIService.class);
    public static ClientAPIService INSTANCE;


    public final NetworkService network;
    public final RenderingService rendering;
    public final UIService ui;
    public final ConsoleAPI console;
    public final CursorService cursor;
    public final CameraService camera;
    public final LightingService lighting;
    public final AudioService audio;
    public final GamepadService gamepad;
    public final ClientSharedApiProxy shared;
    private final ClientUpdateManager updateManager;
    public final EventService events;

    public InputService input;

    ClientScriptingRuntime scriptingRuntime;
    private final AtomicBoolean contextUpdated = new AtomicBoolean(false);

    public ClientAPIService() {
        if (INSTANCE != null) {
            LOGGER.warn("ClientAPIService is being instantiated more than once. This may lead to unexpected behavior.");
        }
        INSTANCE = this;

        this.network = new NetworkService();
        this.rendering = new RenderingService();
        this.ui = new UIService();
        this.cursor = new CursorService();
        this.console = new ConsoleAPI();
        this.camera = new CameraService();
        this.lighting = new LightingService();
        this.audio = new AudioService();
        this.gamepad = new GamepadService();
        this.shared = new ClientSharedApiProxy();
        this.events = new EventService(this);

        this.network.setLightingService(this.lighting);
        this.network.setAudioService(this.audio);

        this.updateManager = new ClientUpdateManager(this);

        LOGGER.info("ClientAPIService partially initialized. Waiting for scripting runtime...");
    }

    public void setRuntime(ClientScriptingRuntime runtime) {
        if (this.scriptingRuntime != null) {
            LOGGER.warn("Scripting runtime is being set more than once.");
            return;
        }
        this.scriptingRuntime = runtime;
        if (this.audio != null) {
            this.audio.setRuntime(runtime);
        }
        this.input = new InputService(runtime);
        LOGGER.info("InputService initialized.");

        if (runtime != null && runtime.isInitialized()) {
            runtime.updateMoudBindings();
        }
    }

    public void updateScriptingContext(Context context) {
        if (context == null) {
            LOGGER.warn("Attempted to update with null context");
            contextUpdated.set(false);
            return;
        }

        try {
            ExecutorService executor = this.scriptingRuntime != null ? this.scriptingRuntime.getExecutor() : null;
            this.network.setContext(context);
            this.rendering.setContext(context);
            this.ui.setContext(context);
            this.ui.setExecutor(executor);
            this.console.setContext(context);
            this.camera.setContext(context);
            this.lighting.setContext(context);
            this.audio.setContext(context);
            this.gamepad.setContext(context);
            this.gamepad.setExecutor(executor);
            this.events.setContext(context);

            if (this.input != null) {
                this.input.setContext(context);

                if (scriptingRuntime != null) {
                    scriptingRuntime.updateMoudBindings();
                }
            } else {
                LOGGER.warn("Attempted to update script context before InputService was initialized.");
            }

            contextUpdated.set(true);
            LOGGER.debug("ClientAPIService updated scripting context for all child services.");
        } catch (Exception e) {
            LOGGER.error("Failed to update scripting context", e);
            contextUpdated.set(false);
        }
    }

    public boolean isContextValid() {
        return contextUpdated.get();
    }

    public void cleanup() {
        contextUpdated.set(false);

        try {
            if (network != null) network.cleanUp();
            if (rendering != null) rendering.cleanUp();
            if (ui != null) ui.cleanUp();
            if (console != null) console.cleanUp();
            if (camera != null) camera.cleanUp();
            if (cursor != null) cursor.cleanUp();
            if (lighting != null) lighting.cleanUp();
            if (audio != null) audio.cleanUp();
            if (gamepad != null) gamepad.cleanUp();
            if (input != null) input.cleanUp();
            if (updateManager != null) updateManager.cleanup();
            if (events != null) events.cleanUp();

            this.scriptingRuntime = null;
            LOGGER.info("ClientAPIService cleaned up all child services.");
        } catch (Exception e) {
            LOGGER.error("Error during cleanup", e);
        }
    }

    public ClientUpdateManager getUpdateManager() {
        return updateManager;
    }
}
