package com.moud.client.api.service;

public final class ClientAPIService {

    public final NetworkService network;
    public final RenderingService rendering;
    public final UIService ui;
    public final ConsoleAPI console;

    public ClientAPIService() {
        this.network = new NetworkService();
        this.rendering = new RenderingService();
        this.ui = new UIService();
        this.console = new ConsoleAPI();
    }

}