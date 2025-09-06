package com.moud.client.api.service;

import com.moud.client.update.ClientUpdateManager;

public final class ClientAPIService {

    public final NetworkService network;
    public final RenderingService rendering;
    public final UIService ui;
    public final ConsoleAPI console;
    public final CameraService camera;
    public final InputService input;
    private final ClientUpdateManager updateManager;

    public ClientAPIService() {
        this.network = new NetworkService();
        this.rendering = new RenderingService();
        this.ui = new UIService();
        this.console = new ConsoleAPI();
        this.camera = new CameraService();
        this.input = new InputService();
        this.updateManager = new ClientUpdateManager(this);
    }

}