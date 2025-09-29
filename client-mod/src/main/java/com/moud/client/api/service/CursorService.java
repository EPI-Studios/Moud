package com.moud.client.api.service;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CursorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorService.class);
    private final MinecraftClient client;
    private boolean isVisible = false;
    private boolean forceHidden = false;
    private boolean wasInGame = false;

    public CursorService() {
        this.client = MinecraftClient.getInstance();
    }

    public void show() {
        if (!isVisible) {
            this.isVisible = true;
            this.forceHidden = false;
            client.execute(() -> {
                if (client.mouse != null) {
                    client.mouse.unlockCursor();
                }
            });
            LOGGER.debug("Cursor shown.");
        }
    }

    public void hide() {
        if (isVisible || !forceHidden) {
            this.isVisible = false;
            this.forceHidden = true;
            client.execute(() -> {
                if (client.mouse != null) {
                    client.mouse.lockCursor();
                }
            });
            LOGGER.debug("Cursor hidden.");
        }
    }

    public void toggle() {
        if (isVisible) {
            hide();
        } else {
            show();
        }
    }

    public boolean isVisible() {
        return this.isVisible;
    }

    public void tick() {
        if (client.world == null) return;

        boolean inGame = client.currentScreen == null;

        if (forceHidden && inGame) {
            if (client.mouse != null && !client.mouse.isCursorLocked()) {
                client.execute(() -> client.mouse.lockCursor());
            }
        }

        if (!wasInGame && inGame && forceHidden) {
            client.execute(() -> {
                if (client.mouse != null) {
                    client.mouse.lockCursor();
                }
            });
        }

        wasInGame = inGame;
    }

    public void onScreenOpen(Screen screen) {
        if (screen != null && forceHidden) {
            client.execute(() -> {
                if (client.mouse != null) {
                    client.mouse.unlockCursor();
                }
            });
        }
    }

    public void onScreenClose() {
        if (forceHidden) {
            client.execute(() -> {
                if (client.mouse != null) {
                    client.mouse.lockCursor();
                }
            });
        }
    }

    public void onFocusGained() {
        if (forceHidden && client.currentScreen == null) {
            client.execute(() -> {
                if (client.mouse != null) {
                    client.mouse.lockCursor();
                }
            });
        }
    }

    public void cleanUp() {
        if (isVisible) {
            hide();
        }
        this.forceHidden = false;
        LOGGER.info("CursorService cleaned up.");
    }
}