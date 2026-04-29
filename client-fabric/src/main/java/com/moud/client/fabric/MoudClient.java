package com.moud.client.fabric;

import com.moud.client.fabric.editor.diagnostics.ClientFrameProfiler;
import com.moud.client.fabric.input.ClientInputMap;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.player.ClientPlayerMotionController;
import com.moud.client.fabric.render.PostProcessStage;
import com.moud.client.fabric.render.VeilSceneRenderer;
import com.moud.client.fabric.render.hud.HudCanvasRenderer;
import com.moud.client.fabric.render.hud.HudSelectionOverlay;
import com.moud.client.fabric.render.loading.PlayLoadingOverlay;
import com.moud.client.fabric.util.ClientDebugLog;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

final class MoudClient {
    private final MoudClientContext ctx = new MoudClientContext();
    private final MoudClientBootstrap bootstrap = new MoudClientBootstrap(ctx);
    private final ClientUriFlow uriFlow = new ClientUriFlow(ctx);
    private final EditorOverlayController overlayController = new EditorOverlayController(ctx);
    private final ClientMessageDispatcher dispatcher = new ClientMessageDispatcher(ctx);
    private final ClientSessionLifecycle sessionLifecycle =
            new ClientSessionLifecycle(ctx, overlayController, dispatcher);

    void init() {
        bootstrap.registerPayloads();
        bootstrap.registerKeybindings();
        registerLifecycleEvents();
        bootstrap.initializeSubsystems();
    }

    private void registerLifecycleEvents() {
        ClientTickEvents.END_WORLD_TICK.register(world -> bootstrap.registerFileDropCallback());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> MoudClientBootstrap.initIcons());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ctx.uriServer.close());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(sessionLifecycle::onJoin));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(sessionLifecycle::onDisconnect));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // two-stage post fx: WORLD here for depth-aware passes, SCREEN later for screen-space
        WorldRenderEvents.LAST.register(context -> VeilSceneRenderer.runFullscreenPostProcess(
                MinecraftClient.getInstance(),
                PostProcessStage.WORLD));
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderOverlays(drawContext));
    }

    private void tick(MinecraftClient client) {
        if (client == null) return;

        MinecraftGhostBlocks.get().clientTick();

        sessionLifecycle.handleSessionLifecycle(client);
        uriFlow.tick(client);
        overlayController.handleEditorToggle(client);
        overlayController.handleInputBlocking(client);
        overlayController.handleOverlayState();
        tickSystems();
        ClientPlayerMotionController.clientTick(client);
        ClientInputMap.poll();
    }

    private void tickSystems() {
        if (ctx.overlayOpen && ctx.isConnected()) {
            ctx.assets.tick(ctx.session);
        }
        ctx.sceneAudio.tick(MinecraftClient.getInstance(), ctx.playRuntime.isActive() && ctx.isConnected());
        if (ctx.playRuntime.isActive() && ctx.session != null) {
            ctx.playRuntime.tick(ctx.session);
            ctx.uiInputTracker.tick(ctx.session, ctx.playRuntime);
        }
        if (ctx.session != null) ctx.session.tick();
    }

    private void renderOverlays(DrawContext drawContext) {
        boolean isConnected = ctx.isConnected();
        if (isConnected) {
            ClientFrameProfiler.beginScope("overlay.hud");
            try {
                HudCanvasRenderer.render(drawContext, MinecraftClient.getInstance());
                HudSelectionOverlay.render(drawContext, MinecraftClient.getInstance());
            } finally {
                ClientFrameProfiler.endScope();
            }
        }

        if (isConnected && ctx.overlayOpen && ctx.overlay != null) {
            ClientFrameProfiler.beginScope("overlay.editor");
            try {
                ctx.overlay.render(ctx.session);
            } catch (Throwable t) {
                ClientDebugLog.error("EditorOverlay.render crashed", t);
            } finally {
                ClientFrameProfiler.endScope();
            }
        }

        PlayLoadingOverlay.render(drawContext);

        // runs after hud/overlays so screen filters (pixelation etc) sample the composite
        VeilSceneRenderer.runFullscreenPostProcess(
                MinecraftClient.getInstance(),
                PostProcessStage.SCREEN);

        ClientFrameProfiler.endFrame();
    }
}
