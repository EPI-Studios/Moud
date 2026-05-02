package com.moud.client.fabric;

import com.moud.client.fabric.assets.MoudAudioAssets;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.model.ModelCache;
import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.client.fabric.net.EnginePayload;
import com.moud.client.fabric.net.FabricEngineTransport;
import com.moud.client.fabric.physics.rapier.ClientRapierPhysics;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.player.ClientPlayerMotionController;
import com.moud.client.fabric.player.PlayerBodyAttachmentCache;
import com.moud.client.fabric.player.PlayerBodyScale;
import com.moud.client.fabric.player.PlayerBodyVisibility;
import com.moud.client.fabric.player.PlayerHeadLook;
import com.moud.client.fabric.player.RemotePlayerStateCache;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.render.env.VeilWorldEnvironmentRenderer;
import com.moud.client.fabric.render.loading.PlayLoading;
import com.moud.client.fabric.runtime.CameraLookTarget;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.scene.ClientSchemaBus;
import com.moud.net.protocol.ServerHello;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.transport.Lane;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

final class ClientSessionLifecycle {
    private final MoudClientContext ctx;
    private final EditorOverlayController overlayController;
    private final ClientMessageDispatcher dispatcher;

    ClientSessionLifecycle(MoudClientContext ctx, EditorOverlayController overlayController,
                           ClientMessageDispatcher dispatcher) {
        this.ctx = ctx;
        this.overlayController = overlayController;
        this.dispatcher = dispatcher;
    }

    void onJoin() {
        resetClientState();
        ctx.editorContext.setOverlay(ctx.overlay);
    }

    void onDisconnect() {
        resetClientState();
        if (ctx.overlay != null) ctx.pendingOverlayDispose = true;
        ctx.editorContext.setOverlay(ctx.overlay);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen == null) client.mouse.lockCursor();
    }

    private void resetClientState() {
        ctx.transport = null;
        ctx.session = null;
        ClientSessionBus.set(null);
        ctx.overlayOpen = false;
        ctx.lastSchema = null;
        ctx.lastSceneList = null;
        ctx.lastSnapshot = null;
        ctx.nextSceneSnapshotRequestId = 1L;
        ctx.initialSnapshotRequested = false;
        ctx.initialManifestRequested = false;
        ctx.autoOpenedEditor = false;

        ClientSceneBus.clear();
        ClientSchemaBus.clear();
        PlayerBodyAttachmentCache.clear();
        PlayerBodyVisibility.clear();
        PlayerBodyScale.clear();
        PlayerHeadLook.clear();
        CameraLookTarget.clear();
        VeilSceneNodeRenderer.clearLights();
        VeilSceneNodeRenderer.clearMaterialTextureCache();
        VeilSceneNodeRenderer.clearCollisionGeometryCache();
        ClientRapierPhysics.clearCollisionGeometry();
        VeilWorldEnvironmentRenderer.clear();
        MoudTextures.clear();
        MoudTextAssets.clear();
        MoudAudioAssets.clear();
        ModelCache.clear();
        ctx.sceneAudio.clear(MinecraftClient.getInstance());

        ctx.camera.setEnabled(false);
        ctx.camera.resetBootstrap();
        MinecraftGhostBlocks.get().cancel();
        ctx.playRuntime.onDisconnect();
        ctx.pendingRuntimeOps.clear();
        ClientPlayerMotionController.reset();
        PlayLoading.cancel();

        if (ctx.overlay != null) ctx.overlay.setOpen(false);
    }

    void handleSessionLifecycle(MinecraftClient client) {
        boolean isConnected = ctx.isConnected();

        if (isConnected) {
            if (!ctx.initialSnapshotRequested && ctx.lastSnapshot == null) {
                ctx.initialSnapshotRequested = true;
                ctx.session.send(Lane.STATE, new SceneSnapshotRequest(ctx.nextSceneSnapshotRequestId++));
            }
            if (!ctx.initialManifestRequested) {
                ctx.initialManifestRequested = true;
                ctx.assets.requestManifest(ctx.session);
            }
            if (!ctx.autoOpenedEditor) {
                ServerHello hello = ctx.session.serverHello();
                if (hello != null) {
                    ctx.autoOpenedEditor = true;
                    if (hello.devMode() && !ctx.overlayOpen) {
                        overlayController.openEditorOverlay(client, false);
                    } else if (!ctx.overlayOpen) {
                        PlayLoading.begin();
                        PlayLoading.pushStatus("server", "Connecting to server");
                    }
                }
            }
            overlayController.syncEditorModeToServer();
        } else {
            ctx.lastEditorModeSent = null;
        }

        ctx.playRuntime.setActive(isConnected && (!ctx.overlayOpen || ctx.playInViewport));

        if (ctx.transport == null && ctx.session == null && ClientPlayNetworking.canSend(EnginePayload.ID)) {
            ctx.transport = new FabricEngineTransport();
            ctx.session = new Session(SessionRole.CLIENT, ctx.transport);
            ClientSessionBus.set(ctx.session);
            ctx.session.setLogSink(System.out::println);
            ctx.session.setMessageHandler(dispatcher::onMessage);
            ctx.session.start();
        }
    }
}
