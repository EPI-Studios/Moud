package com.moud.client.editor;

import com.moud.client.editor.assets.EditorAssetCatalog;
import com.moud.client.editor.camera.EditorCameraController;
import com.moud.client.editor.config.EditorConfig;
import com.moud.client.editor.picking.RaycastPicker;
import com.moud.client.editor.runtime.RuntimeObjectRegistry;
import com.moud.client.editor.scene.SceneHistoryManager;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.BlueprintCornerSelector;
import com.moud.client.editor.selection.SceneSelectionManager;
import com.moud.client.editor.ui.EditorImGuiLayer;
import com.moud.client.editor.ui.SceneEditorOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;


public final class EditorModeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MoudEditorMode");
    private static final EditorModeManager INSTANCE = new EditorModeManager();

    private boolean active;
    private boolean unlockCursorOnDisable;
    private final EditorCameraController cameraController = EditorCameraController.getInstance();
    private GameMode previousGameMode = null;

    private EditorModeManager() {
    }

    public static EditorModeManager getInstance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }


    public boolean toggle() {
        setActive(!active);
        return active;
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (active) {
            onActivated();
        } else {
            onDeactivated();
        }
    }

    private void onActivated() {
        LOGGER.info("Moud editor mode enabled");
        SceneSessionManager.getInstance().onEditorActivated();
        EditorAssetCatalog.getInstance().requestAssetsIfNeeded();
        cameraController.enable(SceneEditorOverlay.getInstance().getSelectedObject());
        EditorImGuiLayer.getInstance().resetMouseState();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.mouse != null && client.mouse.isCursorLocked()) {
            unlockCursorOnDisable = true;
            client.mouse.unlockCursor();
        } else {
            unlockCursorOnDisable = false;
        }
        if (client.player != null) {
            GameMode gm = client.interactionManager != null ? client.interactionManager.getCurrentGameMode() : null;
            previousGameMode = gm;
            if (client.interactionManager != null) {
                client.interactionManager.setGameMode(GameMode.SPECTATOR);
            }

            client.player.setInvisible(true);
        } else {
            previousGameMode = null;
        }
   }

    private void onDeactivated() {
        LOGGER.info("Moud editor mode disabled");
        SceneSessionManager.getInstance().onEditorDeactivated();
        SceneHistoryManager.getInstance().flushPendingChange();
        cameraController.disable();
        BlueprintCornerSelector.getInstance().cancel();
        RuntimeObjectRegistry.getInstance().syncPlayers(Collections.emptyList());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            if (client.interactionManager != null && previousGameMode != null) {
                client.interactionManager.setGameMode(previousGameMode);
            }

            client.player.setInvisible(false);
        }
        if (unlockCursorOnDisable && client != null && client.currentScreen == null && client.mouse != null) {
            client.mouse.lockCursor();
        }
        unlockCursorOnDisable = false;
        previousGameMode = null;
    }


    public void tick(@Nullable MinecraftClient client) {
        if (!active || client == null || client.player == null) {
            return;
        }
        cameraController.tick();
        EditorImGuiLayer.getInstance().tick();
        if (client.world != null) {
            RuntimeObjectRegistry.getInstance().syncPlayers(client.world.getPlayers());
        } else {
            RuntimeObjectRegistry.getInstance().syncPlayers(Collections.emptyList());
        }
        RaycastPicker.getInstance().updateHover();
    }

    public boolean consumeKeyEvent(int key, int scancode, int action, int modifiers) {
        if (!active) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }


        if (cameraController.handleKeyPress(key, action)) {
            return true;
        }

        if (action == GLFW.GLFW_PRESS && key == EditorConfig.getInstance().camera().focusKey) {
            cameraController.focusSelection(SceneEditorOverlay.getInstance().getSelectedObject());
            return true;
        }
        if (handleHistoryShortcuts(key, action, modifiers)) {
            return true;
        }

        return false;
    }

    public boolean consumeCharEvent(int codePoint) {
        if (!active) {
            return false;
        }
        return true;
    }

    public boolean consumeMouseButton(int button, int action, int mods, double cursorX, double cursorY) {
        if (!active) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }

        if (cameraController.handleMouseButton(button, action, mods, cursorX, cursorY)) {
            return true;
        }

        if (BlueprintCornerSelector.getInstance().handleMouseButton(button, action)) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            if (EditorImGuiLayer.getInstance().isMouseOverUI()) {
                return false;
            }

            RaycastPicker picker = RaycastPicker.getInstance();
            picker.updateHover();
            var hovered = picker.getHoveredObject();
            if (hovered != null) {
                picker.selectHovered();
                SceneEditorOverlay.getInstance().selectRuntimeObject(hovered);
                return true;
            }

            if (SceneSelectionManager.getInstance().handleClickSelection()) {
                return true;
            }
            return true; // prevent vanilla block interactions while editor is active
        }

        return cameraController.isCapturingInput();
    }

    public boolean consumeMouseScroll(double horizontal, double vertical) {
        if (!active) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }
        return cameraController.handleScroll(horizontal, vertical);
    }

    public boolean consumeMouseDelta(double deltaX, double deltaY) {
        if (!active) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }
        return cameraController.handleMouseDelta(deltaX, deltaY);
    }

    private boolean handleHistoryShortcuts(int key, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS) {
            return false;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (shift) {
                SceneHistoryManager.getInstance().redo();
            } else {
                SceneHistoryManager.getInstance().undo();
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            SceneHistoryManager.getInstance().redo();
            return true;
        }
        return false;
    }
}
