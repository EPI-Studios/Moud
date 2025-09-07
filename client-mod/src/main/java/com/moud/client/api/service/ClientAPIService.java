package com.moud.client.api.service;

import com.moud.client.update.ClientUpdateManager;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientAPIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAPIService.class);

    public final NetworkService network;
    public final RenderingService rendering;
    public final UIService ui;
    public final ConsoleAPI console;
    public final CameraService camera;
    public final InputService input;
    private final ClientUpdateManager updateManager;

    public static ClientAPIService INSTANCE;

    public ClientAPIService() {
        INSTANCE = this;
        this.network = new NetworkService();
        this.rendering = new RenderingService();
        this.ui = new UIService();
        this.console = new ConsoleAPI();
        this.camera = new CameraService();
        this.input = new InputService();


        this.updateManager = new ClientUpdateManager(this);
        LOGGER.info("ClientAPIService initialized and registered ClientUpdateManager.");
    }

    public void updateScriptingContext(Context context) {
        this.network.setContext(context);
        this.rendering.setContext(context);
        this.ui.setContext(context);
        this.console.setContext(context);
        this.camera.setContext(context);
        this.input.setContext(context);

        LOGGER.debug("ClientAPIService updated scripting context for child services.");
    }


    public void cleanup() {
        network.cleanUp();
        rendering.cleanUp();
        ui.cleanUp();
        console.cleanUp();
        camera.cleanUp();
        input.cleanUp();

        updateManager.cleanup();
        LOGGER.info("ClientAPIService cleaned up all child services.");
    }

    public ClientUpdateManager getUpdateManager() {
        return updateManager;
    }
}