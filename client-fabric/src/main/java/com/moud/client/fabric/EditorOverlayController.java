package com.moud.client.fabric;

import com.moud.client.fabric.editor.overlay.EditorOverlay;
import com.moud.client.fabric.net.EnginePayload;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.render.loading.PlayLoading;
import com.moud.net.protocol.EditorModeChanged;
import com.moud.net.protocol.RequestRespawn;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

final class EditorOverlayController {
    private final MoudClientContext ctx;

    EditorOverlayController(MoudClientContext ctx) {
        this.ctx = ctx;
    }

    void handleEditorToggle(MinecraftClient client) {
        while (ctx.toggleKey != null && ctx.toggleKey.wasPressed()) {
            if (!ctx.overlayOpen) openEditorOverlay(client, true);
            else closeEditorOverlay(client);
        }
        while (ctx.viewportPlayKey != null && ctx.viewportPlayKey.wasPressed()) {
            if (ctx.overlayOpen) toggleViewportPlay(client);
        }
        while (ctx.collisionDebugKey != null && ctx.collisionDebugKey.wasPressed()) {
            VeilSceneNodeRenderer.toggleCollisionDebug();
        }
        while (ctx.physicsDebugKey != null && ctx.physicsDebugKey.wasPressed()) {
            // rapier client physics debug overlay is deferred
        }
    }

    private void toggleViewportPlay(MinecraftClient client) {
        ctx.playInViewport = !ctx.playInViewport;
        ctx.camera.setEnabled(!ctx.playInViewport);
        if (ctx.playInViewport) {
            ctx.editorContext.setViewportInputFocused(true);
            if (client != null && client.currentScreen == null && client.mouse != null) {
                client.mouse.lockCursor();
            }
        } else {
            ctx.editorContext.setViewportInputFocused(false);
            if (client != null && client.mouse != null) client.mouse.unlockCursor();
        }
        ctx.lastEditorModeSent = null;
    }

    void handleInputBlocking(MinecraftClient client) {
        if (ctx.playRuntime.isActive() && client.currentScreen == null) {
            ctx.playRuntime.captureInput();
        }
        boolean editorConsumingInput = ctx.overlayOpen && !ctx.playInViewport;
        if ((editorConsumingInput || ctx.playRuntime.shouldBlockVanillaInput(client)) && client.currentScreen == null) {
            blockVanillaInput(client);
        }
        if (ctx.playRuntime.isActive() && client.currentScreen == null) {
            ctx.playRuntime.applyCursorMode(client);
        }
        if (ctx.overlayOpen && !ctx.camera.isCapturing() && !ctx.editorContext.isViewportInputFocused()) {
            long windowHandle = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    void handleOverlayState() {
        if (ctx.pendingOverlayDispose && ctx.overlay != null) {
            ctx.pendingOverlayDispose = false;
            if (GLFW.glfwGetCurrentContext() != 0L) ctx.overlay.close();
            ctx.overlay = null;
            ctx.editorContext.setOverlay(null);
        }

        if (!ctx.overlayOpen || !ctx.isConnected()) return;

        if (ctx.overlay == null) {
            ctx.overlay = new EditorOverlay(ctx.assets);
            ctx.overlay.setOpen(true);
            applyBufferedStateToOverlay();
            ctx.overlay.requestSnapshot(ctx.session);
            ctx.editorContext.setOverlay(ctx.overlay);
            return;
        }

        if (!ctx.overlay.isOpen()) {
            ctx.overlay.setOpen(true);
            applyBufferedStateToOverlay();
            ctx.overlay.requestSnapshot(ctx.session);
            ctx.editorContext.setOverlay(ctx.overlay);
        }
    }

    void syncEditorModeToServer() {
        if (!ctx.isConnected()) return;
        boolean editorMode = ctx.overlayOpen && !ctx.playInViewport;
        if (ctx.lastEditorModeSent != null && ctx.lastEditorModeSent == editorMode) return;
        ctx.session.send(Lane.STATE, new EditorModeChanged(editorMode));
        ctx.lastEditorModeSent = editorMode;
    }

    void openEditorOverlay(MinecraftClient client, boolean showMessageIfDisconnected) {
        if (ctx.overlayOpen) return;

        if (!ClientPlayNetworking.canSend(EnginePayload.ID)) {
            if (showMessageIfDisconnected && client != null && client.inGameHud != null) {
                client.inGameHud.setOverlayMessage(Text.literal("MOUD editor: connect to a MOUD server"), false);
            }
            return;
        }

        ctx.overlayOpen = true;
        ctx.pendingRestoreSnapshot = true;
        ctx.camera.setEnabled(true);

        if (client != null && client.mouse != null) client.mouse.unlockCursor();

        if (ctx.isConnected()) {
            ctx.session.send(Lane.STATE, new EditorModeChanged(true));
            ctx.lastEditorModeSent = true;
        }

        if (ctx.overlay != null && ctx.isConnected()) {
            ctx.overlay.setOpen(true);
            ctx.overlay.requestSnapshot(ctx.session);
        }

        ctx.editorContext.setOverlay(ctx.overlay);
    }

    void closeEditorOverlay(MinecraftClient client) {
        ctx.overlayOpen = false;
        ctx.playInViewport = false;
        ctx.editorContext.setViewportInputFocused(false);
        ctx.camera.setEnabled(false);
        ctx.playRuntime.onEditorClosed();
        MinecraftGhostBlocks.get().cancel();

        if (ctx.overlay != null) ctx.overlay.setOpen(false);

        if (client != null && client.currentScreen == null && client.mouse != null) {
            client.mouse.lockCursor();
        }

        ctx.editorContext.setOverlay(ctx.overlay);

        if (ctx.overlay != null) ctx.overlay.saveAllOpenEditors();

        if (ctx.isConnected()) {
            PlayLoading.begin();
            PlayLoading.pushStatus("server", "Waiting for server");
            ctx.session.send(Lane.STATE, new EditorModeChanged(false));
            ctx.lastEditorModeSent = false;
            ctx.session.send(Lane.EVENTS, new RequestRespawn());
            ctx.session.send(Lane.STATE, new SceneSnapshotRequest(ctx.nextSceneSnapshotRequestId++));
        }
    }

    void applyBufferedStateToOverlay() {
        if (ctx.overlay == null) return;
        if (ctx.lastSchema != null) ctx.overlay.onSchema(ctx.lastSchema);
        if (ctx.lastSceneList != null) ctx.overlay.onSceneList(ctx.lastSceneList);
        if (ctx.lastSnapshot != null) ctx.overlay.onSnapshot(ctx.lastSnapshot);
    }

    private static void blockVanillaInput(MinecraftClient client) {
        if (client == null) return;
        GameOptions options = client.options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.jumpKey.setPressed(false);
        options.sneakKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.attackKey.setPressed(false);
        options.useKey.setPressed(false);
        options.pickItemKey.setPressed(false);
        options.dropKey.setPressed(false);
        options.inventoryKey.setPressed(false);
        options.swapHandsKey.setPressed(false);
    }
}
