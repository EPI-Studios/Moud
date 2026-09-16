package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.client.editor.Editor;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.script.api.GameRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.OptionsScreen;

public final class GameState implements GameRef {

    public static final GameState INSTANCE = new GameState();

    private volatile boolean paused;
    private volatile String reason = "escape";

    private GameState() {}

    public boolean pausing() {
        Minecraft client = Minecraft.getInstance();
        return paused && client.hasSingleplayerServer() && client.getSingleplayerServer() != null && !client.getSingleplayerServer().isPublished();
    }

    public void focusLost(boolean lost) {
        reason = lost ? "focusLost" : "escape";
    }

    public String reason() {
        return reason;
    }

    public void reset() {
        paused = false;
    }

    @Override
    public boolean paused() {
        return paused;
    }

    @Override
    public void paused(boolean on) {
        paused = on;
    }

    @Override
    public void quit() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (Game.standalone()) {
                client.stop();
            } else if (EditMode.allowed() && EditMode.session() > 0) {
                EditMode.request(true);
            } else if (Editor.available()) {
                Editor.requestCloseProject();
            } else {
                client.stop();
            }
        });
    }

    @Override
    public void openSettings() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreen(new OptionsScreen(client.screen, client.options, client.level != null)));
    }

    @Override
    public boolean exported() {
        return Game.standalone();
    }
}
