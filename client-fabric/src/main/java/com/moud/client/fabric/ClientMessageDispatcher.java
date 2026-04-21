package com.moud.client.fabric;

import com.moud.client.fabric.editor.overlay.EditorOverlay;
import com.moud.client.fabric.physics.ClientPhysicsWorld;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.player.ClientPlayerMotionController;
import com.moud.client.fabric.player.RemotePlayerStateCache;
import com.moud.client.fabric.render.InstanceDataStore;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.render.loading.PlayLoading;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.scene.ClientSchemaBus;
import com.moud.client.fabric.scripting.ClientScriptMessageDispatcher;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.net.protocol.AssetPathOpAck;
import com.moud.net.protocol.CollisionGeometrySnapshot;
import com.moud.net.protocol.CursorState;
import com.moud.net.protocol.EditorDiagnosticEvent;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.MultiMeshData;
import com.moud.net.protocol.PlayReady;
import com.moud.net.protocol.PlayerClientState;
import com.moud.net.protocol.PlayerMotion;
import com.moud.net.protocol.ProjectCreateAck;
import com.moud.net.protocol.ProjectInfo;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.protocol.SceneCreateAck;
import com.moud.net.protocol.SceneDeleteAck;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.protocol.ScriptMessage;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

final class ClientMessageDispatcher {
    private final MoudClientContext ctx;

    ClientMessageDispatcher(MoudClientContext ctx) {
        this.ctx = ctx;
    }

    void onMessage(Lane lane, Message message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && !client.isOnThread()) {
            client.execute(() -> onMessage(lane, message));
            return;
        }

        if (ClientDebugLog.enabled() && message != null) {
            ClientDebugLog.debug("recv lane=" + lane + " type=" + message.type());
        }

        if (lane == Lane.ASSETS) {
            ctx.assets.onMessage(message);
            return;
        }

        if (message instanceof ScriptMessage scriptMessage) {
            ClientScriptMessageDispatcher.dispatch(scriptMessage);
            return;
        }

        handleEngineMessage(message);
    }

    private void handleEngineMessage(Message message) {
        boolean overlayReady = ctx.overlay != null && ctx.overlay.isOpen();

        if (message instanceof RuntimeState state) {
            ctx.playRuntime.onRuntimeState(state);
        } else if (message instanceof CursorState state) {
            ctx.playRuntime.onCursorState(state);
        } else if (message instanceof EditorDiagnosticEvent diagnostic) {
            handleEditorDiagnostic(diagnostic);
        } else if (message instanceof ProjectInfo info && overlayReady) {
            PlayLoading.setGameName(info.name());
            ctx.overlay.onProjectInfo(info);
        } else if (message instanceof ProjectCreateAck ack && overlayReady) {
            ctx.overlay.onProjectCreateAck(ack);
        } else if (message instanceof ScriptActionListResponse response && overlayReady) {
            ctx.overlay.onScriptActionListResponse(response);
        } else if (message instanceof ScriptActionInvokeAck ack && overlayReady) {
            ctx.overlay.onScriptActionInvokeAck(ack);
        } else if (message instanceof ScriptFileReadResponse response && overlayReady) {
            ctx.overlay.onScriptFileReadResponse(response);
        } else if (message instanceof ScriptFileWriteAck ack && overlayReady) {
            ctx.overlay.onScriptFileWriteAck(ack);
        } else if (message instanceof AssetPathOpAck ack) {
            handleAssetPathOpAck(ack);
        } else if (message instanceof SceneSaveAck ack) {
            handleSceneAck(overlayReady, ack, "Saved scene: " + ack.sceneId(),
                    "Save failed (" + ack.sceneId() + "): ", ack.success(), ack.error(),
                    o -> o.onSceneSaveAck(ack));
        } else if (message instanceof SceneCreateAck ack) {
            handleSceneAck(overlayReady, ack, "Created scene: " + ack.sceneId(),
                    "Create failed (" + ack.sceneId() + "): ", ack.success(), ack.error(),
                    o -> o.onSceneCreateAck(ack));
        } else if (message instanceof SceneDeleteAck ack) {
            handleSceneAck(overlayReady, ack, "Deleted scene: " + ack.sceneId(),
                    "Delete failed (" + ack.sceneId() + "): ", ack.success(), ack.error(),
                    o -> o.onSceneDeleteAck(ack));
        } else if (message instanceof SceneSnapshot snapshot) {
            handleSnapshot(snapshot);
        } else if (message instanceof SceneOpBatch batch) {
            handleSceneOpBatch(batch);
        } else if (message instanceof SchemaSnapshot schema) {
            ctx.lastSchema = schema;
            ClientSchemaBus.set(schema);
            if (ctx.overlay != null) ctx.overlay.onSchema(schema);
        } else if (message instanceof SceneList list) {
            ctx.lastSceneList = list;
            if (ctx.overlay != null) ctx.overlay.onSceneList(list);
        } else if (message instanceof SceneOpAck ack) {
            if (ctx.overlay != null) ctx.overlay.onAck(ack);
            MinecraftGhostBlocks.get().onAck(ack);
        } else if (message instanceof MultiMeshData mmData) {
            InstanceDataStore.accumulate(mmData.nodeId(), mmData.offset(), mmData.total(), mmData.data());
        } else if (message instanceof CollisionGeometrySnapshot cg) {
            ClientPhysicsWorld.onCollisionGeometry(cg);
            VeilSceneNodeRenderer.onCollisionGeometry(cg);
        } else if (message instanceof PlayerMotion motion) {
            ClientPlayerMotionController.onPlayerMotion(motion);
        } else if (message instanceof PlayerClientState state) {
            RemotePlayerStateCache.apply(state);
        } else if (message instanceof PlayReady ready) {
            PlayLoading.popStatus("server");
            PlayLoading.onServerReady(ready.sceneId());
        }
    }

    private void handleSnapshot(SceneSnapshot snapshot) {
        ctx.lastSnapshot = snapshot;
        if (ctx.pendingRestoreSnapshot) {
            ctx.pendingRestoreSnapshot = false;
            ClientSceneBus.markRestorePending();
        }
        ClientSceneBus.applySnapshot(snapshot);
        if (!ctx.overlayOpen && ctx.playRuntime.isActive() && !ctx.pendingRuntimeOps.isEmpty()) {
            ClientSceneBus.applyOps(List.copyOf(ctx.pendingRuntimeOps));
            ctx.pendingRuntimeOps.clear();
        }
        if (ctx.overlay != null) ctx.overlay.onSnapshot(snapshot);
    }

    private void handleSceneOpBatch(SceneOpBatch batch) {
        if (batch.ops() == null || batch.ops().isEmpty()) return;

        if (ctx.lastSnapshot == null) {
            ctx.pendingRuntimeOps.addAll(batch.ops());
            if (ctx.pendingRuntimeOps.size() > MoudClientContext.MAX_PENDING_RUNTIME_OPS) {
                int keepFrom = Math.max(0, ctx.pendingRuntimeOps.size() - MoudClientContext.MAX_PENDING_RUNTIME_OPS);
                ctx.pendingRuntimeOps.subList(0, keepFrom).clear();
            }
            return;
        }
        if (ctx.playRuntime.isActive() && !ctx.overlayOpen) {
            boolean isPhysics = (batch.batchId() & (1L << 62)) != 0L;
            if (isPhysics) ClientSceneBus.applyPhysicsOps(batch.ops());
            else ClientSceneBus.applyOps(batch.ops());
        }
    }

    private static void handleEditorDiagnostic(EditorDiagnosticEvent diagnostic) {
        if (diagnostic == null) return;
        String source = diagnostic.source() == null || diagnostic.source().isBlank() ? "Runtime" : diagnostic.source();
        String severity = diagnostic.severity() == null ? "" : diagnostic.severity().trim();
        String message = diagnostic.message() == null ? "" : diagnostic.message();
        if ("WARN".equalsIgnoreCase(severity) || "WARNING".equalsIgnoreCase(severity)) {
            ClientDebugLog.warn(source, message);
        } else if ("INFO".equalsIgnoreCase(severity)) {
            ClientDebugLog.info(source, message);
        } else {
            ClientDebugLog.error(source, message);
        }
    }

    private void handleSceneAck(boolean overlayReady, Object ack, String successMsg, String errorPrefix,
                                 boolean success, String error,
                                 Consumer<EditorOverlay> handler) {
        if (overlayReady) {
            handler.accept(ctx.overlay);
            return;
        }
        showHudMessage(success, successMsg, errorPrefix, error);
    }

    private void handleAssetPathOpAck(AssetPathOpAck ack) {
        String label = switch (ack.kind()) {
            case CREATE_FOLDER -> "Created folder";
            case RENAME -> "Renamed";
            case DELETE_RECURSIVE -> "Deleted";
        };
        if (ack.success()) {
            String msg = label + ": " + (ack.newPath().isBlank() ? ack.path() : ack.newPath());
            if (ack.scenesUpdated() > 0) msg += " (" + ack.scenesUpdated() + " scene ref(s) updated)";
            if (ctx.overlay != null) ctx.overlay.getRuntime().requestToast(msg, false, 3000);
            Session s = ctx.session;
            if (s != null) ctx.assets.requestManifest(s);
        } else {
            String error = ack.error() == null || ack.error().isBlank() ? "Unknown error" : ack.error();
            if (ctx.overlay != null) ctx.overlay.getRuntime().requestToast(label + " failed: " + error, true, 4500);
        }
    }

    private static void showHudMessage(boolean success, String successMsg, String errorPrefix, String error) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            String msg = success ? successMsg : errorPrefix + (error == null ? "Unknown error" : error);
            client.inGameHud.setOverlayMessage(Text.literal(msg), false);
        }
    }
}
