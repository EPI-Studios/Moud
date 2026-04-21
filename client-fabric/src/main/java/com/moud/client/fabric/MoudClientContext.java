package com.moud.client.fabric;

import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.audio.SceneAudioManager;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlay;
import com.moud.client.fabric.net.FabricEngineTransport;
import com.moud.client.fabric.platform.MinecraftFreeflyCamera;
import com.moud.client.fabric.render.hud.UiInputTracker;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.client.fabric.uri.ClientUriServer;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.session.Session;
import com.moud.net.session.SessionState;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;

final class MoudClientContext {
    static final int MAX_PENDING_RUNTIME_OPS = 10_000;

    final MinecraftFreeflyCamera camera = new MinecraftFreeflyCamera();
    final EditorContext editorContext = new EditorContext(camera);
    final AssetsClient assets = new AssetsClient();
    final PlayRuntimeClient playRuntime = new PlayRuntimeClient();
    final UiInputTracker uiInputTracker = new UiInputTracker();
    final SceneAudioManager sceneAudio = new SceneAudioManager();
    final ClientUriServer uriServer = new ClientUriServer();
    final ArrayList<SceneOp> pendingRuntimeOps = new ArrayList<>();

    FabricEngineTransport transport;
    Session session;
    EditorOverlay overlay;

    boolean overlayOpen;
    boolean playInViewport;
    Boolean lastEditorModeSent;
    boolean pendingOverlayDispose;
    boolean pendingRestoreSnapshot;
    boolean autoOpenedEditor;
    boolean dropCallbackRegistered;

    KeyBinding toggleKey;
    KeyBinding collisionDebugKey;
    KeyBinding viewportPlayKey;

    volatile SchemaSnapshot lastSchema;
    volatile SceneList lastSceneList;
    volatile SceneSnapshot lastSnapshot;

    long nextSceneSnapshotRequestId = 1L;
    boolean initialSnapshotRequested;
    boolean initialManifestRequested;

    boolean isConnected() {
        return session != null && session.state() == SessionState.CONNECTED;
    }
}
