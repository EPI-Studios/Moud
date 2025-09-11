
package com.moud.client.api.service;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CursorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorService.class);
    private final MinecraftClient client;
    private boolean isVisible = false;

    public CursorService() {
        this.client = MinecraftClient.getInstance();
    }

    /**
     * Shows the cursor and allows it to move freely.
     */
    public void show() {
        if (!isVisible) {
            this.isVisible = true;
            client.execute(() -> client.mouse.unlockCursor());
            LOGGER.debug("Cursor shown.");
        }
    }

    /**
     * Hides and locks the cursor for game interaction.
     */
    public void hide() {
        if (isVisible) {
            this.isVisible = false;
            client.execute(() -> client.mouse.lockCursor());
            LOGGER.debug("Cursor hidden.");
        }
    }

    /**
     * Toggles the visibility and lock state of the cursor.
     */
    public void toggle() {
        if (isVisible) {
            hide();
        } else {
            show();
        }
    }

    /**
     * Checks if the cursor is currently visible and unlocked.
     * @return true if the cursor is visible, false otherwise.
     */
    public boolean isVisible() {
        return this.isVisible;
    }

    /**
     * Called when the client disconnects to reset the state.
     */
    public void cleanUp() {
        // Ensure the cursor is locked when the service is cleaned up
        if (isVisible) {
            hide();
        }
        LOGGER.info("CursorService cleaned up.");
    }
}