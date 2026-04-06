package com.moud.client.fabric;

import com.moud.client.fabric.audio.SceneAudioManager;
import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.assets.MoudAudioAssets;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlay;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.util.AssetImportUtil;
import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.client.fabric.player.ClientPlayerMotionController;
import com.moud.client.fabric.player.MoudPalAnimLayer;
import com.moud.client.fabric.player.PlayerBodyAttachmentCache;
import com.moud.client.fabric.net.EnginePayload;
import com.moud.client.fabric.net.FabricEngineTransport;
import com.moud.client.fabric.platform.MinecraftFreeflyCamera;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.model.ModelCache;
import com.moud.client.fabric.render.InstanceDataStore;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.render.hud.HudCanvasRenderer;
import com.moud.client.fabric.render.hud.UiInputTracker;
import com.moud.client.fabric.render.env.VeilWorldEnvironmentRenderer;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.ProjectCreateAck;
import com.moud.net.protocol.ProjectInfo;
import com.moud.net.protocol.RequestRespawn;
import com.moud.net.protocol.RuntimeState;
import com.moud.net.protocol.CursorState;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneCreateAck;
import com.moud.net.protocol.SceneDeleteAck;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOpBatch;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.protocol.EditorModeChanged;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.MultiMeshData;
import com.moud.net.protocol.PlayerMotion;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.protocol.ServerHello;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;

final class MoudClient {
    private final MinecraftFreeflyCamera camera = new MinecraftFreeflyCamera();
    private final EditorContext editorContext = new EditorContext(camera);
    private final AssetsClient assets = new AssetsClient();
    private final PlayRuntimeClient playRuntime = new PlayRuntimeClient();
    private final UiInputTracker uiInputTracker = new UiInputTracker();
    private final SceneAudioManager sceneAudio = new SceneAudioManager();

    private FabricEngineTransport transport;
    private Session session;
    private EditorOverlay overlay;

    private boolean overlayOpen;
    private Boolean lastEditorModeSent;
    private boolean pendingOverlayDispose;
    private boolean pendingRestoreSnapshot;
    private boolean autoOpenedEditor;
    private KeyBinding toggleKey;
    private boolean dropCallbackRegistered;

    private volatile SchemaSnapshot lastSchema;
    private volatile SceneList lastSceneList;
    private volatile SceneSnapshot lastSnapshot;
    private final ArrayList<SceneOp> pendingRuntimeOps = new ArrayList<>();
    private static final int MAX_PENDING_RUNTIME_OPS = 10_000;

    private long nextSceneSnapshotRequestId = 1L;
    private boolean initialSnapshotRequested;
    private boolean initialManifestRequested;

    void init() {
        registerPayloads();
        registerKeybindings();
        registerLifecycleEvents();
        initializeSubsystems();
    }

    private void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(EnginePayload.ID, EnginePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EnginePayload.ID, EnginePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(EnginePayload.ID, (payload, context) -> {
            if (transport != null) {
                context.client().execute(() -> transport.acceptServerPayload(payload.data()));
            }
        });
    }

    private void registerKeybindings() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.editor",
                GLFW.GLFW_KEY_F8,
                "category.moud"
        ));
    }

    private void registerLifecycleEvents() {
        ClientTickEvents.END_WORLD_TICK.register(world -> registerFileDropCallback());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> initIcons());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(this::onJoin));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::onDisconnect));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderOverlays(drawContext));
    }

    private void initializeSubsystems() {
        EditorOverlayBus.set(editorContext);
        PlayRuntimeBus.set(playRuntime);
        VeilSceneNodeRenderer.init();
        VeilWorldEnvironmentRenderer.init();
        MoudPalAnimLayer.register();
        MoudTextures.init(assets);
        MoudTextAssets.init(assets);
        MoudAudioAssets.init(assets);
        ModelCache.init(assets);
    }

    private void registerFileDropCallback() {
        if (dropCallbackRegistered) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        dropCallbackRegistered = true;
        long windowHandle = client.getWindow().getHandle();

        GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
            EditorContext context = EditorOverlayBus.get();
            if (context == null || !context.isActive() || context.overlay() == null) {
                return;
            }

            EditorRuntime runtime = context.overlay().getRuntime();
            if (runtime == null) {
                return;
            }

            for (int i = 0; i < count; i++) {
                String path = GLFWDropCallback.getName(names, i);
                if (path != null && !path.isBlank()) {
                    Thread.ofVirtual().start(() -> AssetImportUtil.importDroppedFile(runtime, path));
                }
            }
        });
    }

    private static void initIcons() {
        String[] nodeTypes = {
                "Camera3D", "PlayerStart", "PlayerAttachment", "WorldEnvironment",
                "CSGBox", "CSGBlock", "MeshInstance3D", "SceneInstance3D",
                "OmniLight3D", "DirectionalLight3D", "SpotLight3D",
                "Node3D", "Model3D",
                "StaticBody3D", "RigidBody3D", "CharacterBody3D",
                "Area3D", "Raycast3D", "Marker3D"
        };
        for (String type : nodeTypes) {
            MoudIcons.loadFromResource(type, "/assets/moud/icons/" + type + ".png");
        }

        String[] uiIcons = {
                "folder", "folder_open", "chevron_right",
                "file", "scene", "image", "text", "audio", "binary", "model"
        };
        for (String name : uiIcons) {
            MoudIcons.loadFromResource(name, "/assets/moud/icons/" + name + ".png");
        }

        String[] toolIcons = {
                "select", "move", "rotate", "scale", "snap",
                "visible", "invisible", "lock", "unlock",
                "search", "add",
                "chevron_down", "chevron_right",
                "check",
                "code", "image", "text", "file"
        };
        for (String name : toolIcons) {
            MoudIcons.loadFromResource(name, "/assets/moud/icons/" + name + ".png");
        }
    }

    private void resetClientState() {
        transport = null;
        session = null;
        ClientSessionBus.set(null);
        overlayOpen = false;
        lastSchema = null;
        lastSceneList = null;
        lastSnapshot = null;
        nextSceneSnapshotRequestId = 1L;
        initialSnapshotRequested = false;
        initialManifestRequested = false;
        autoOpenedEditor = false;

        ClientSceneBus.clear();
        PlayerBodyAttachmentCache.clear();
        VeilSceneNodeRenderer.clearLights();
        VeilSceneNodeRenderer.clearMaterialTextureCache();
        VeilWorldEnvironmentRenderer.clear();
        MoudTextures.clear();
        MoudTextAssets.clear();
        MoudAudioAssets.clear();
        ModelCache.clear();
        sceneAudio.clear(MinecraftClient.getInstance());

        camera.setEnabled(false);
        camera.resetBootstrap();
        MinecraftGhostBlocks.get().cancel();
        playRuntime.onDisconnect();
        pendingRuntimeOps.clear();
        ClientPlayerMotionController.reset();

        if (overlay != null) {
            overlay.setOpen(false);
        }
    }

    private void onJoin() {
        resetClientState();
        editorContext.setOverlay(overlay);
    }

    private void onDisconnect() {
        resetClientState();
        if (overlay != null) {
            pendingOverlayDispose = true;
        }
        editorContext.setOverlay(overlay);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen == null) {
            client.mouse.lockCursor();
        }
    }

    private void renderOverlays(DrawContext drawContext) {
        boolean isConnected = session != null && session.state() == SessionState.CONNECTED;

        if (isConnected && overlayOpen && overlay != null) {
            try {
                overlay.render(session);
            } catch (Throwable t) {
                ClientDebugLog.error("EditorOverlay.render crashed", t);
            }
        }

        if (isConnected && playRuntime.isActive()) {
            HudCanvasRenderer.render(drawContext, MinecraftClient.getInstance());
        }
    }

    private void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        MinecraftGhostBlocks.get().clientTick();

        handleSessionLifecycle(client);
        handleEditorToggle(client);
        handleInputBlocking(client);
        handleOverlayState();
        tickSystems();
        // anchor override, must run last
        ClientPlayerMotionController.clientTick(client);
    }

    private void handleSessionLifecycle(MinecraftClient client) {
        boolean isConnected = session != null && session.state() == SessionState.CONNECTED;

        if (isConnected) {
            if (!initialSnapshotRequested && lastSnapshot == null) {
                initialSnapshotRequested = true;
                session.send(Lane.STATE, new SceneSnapshotRequest(nextSceneSnapshotRequestId++));
            }
            if (!initialManifestRequested) {
                initialManifestRequested = true;
                assets.requestManifest(session);
            }
            if (!autoOpenedEditor) {
                ServerHello hello = session.serverHello();
                if (hello != null) {
                    autoOpenedEditor = true;
                    if (hello.devMode() && !overlayOpen) {
                        openEditorOverlay(client, false);
                    }
                }
            }
        }

        if (isConnected) {
            syncEditorModeToServer();
        } else {
            lastEditorModeSent = null;
        }

        playRuntime.setActive(isConnected && !overlayOpen);

        if (transport == null && session == null && ClientPlayNetworking.canSend(EnginePayload.ID)) {
            transport = new FabricEngineTransport();
            session = new Session(SessionRole.CLIENT, transport);
            ClientSessionBus.set(session);
            session.setLogSink(System.out::println);
            session.setMessageHandler(this::onMessage);
            session.start();
        }
    }

    private void syncEditorModeToServer() {
        if (session == null || session.state() != SessionState.CONNECTED) {
            return;
        }
        boolean open = overlayOpen;
        if (lastEditorModeSent != null && lastEditorModeSent == open) {
            return;
        }
        session.send(Lane.STATE, new EditorModeChanged(open));
        lastEditorModeSent = open;
    }

    private void handleEditorToggle(MinecraftClient client) {
        while (toggleKey != null && toggleKey.wasPressed()) {
            if (!overlayOpen) {
                openEditorOverlay(client, true);
            } else {
                closeEditorOverlay(client);
            }
        }
    }

    private void handleInputBlocking(MinecraftClient client) {
        if ((overlayOpen || playRuntime.shouldBlockVanillaInput(client)) && client.currentScreen == null) {
            blockVanillaInput(client);
        }

        if (playRuntime.isActive() && client.currentScreen == null) {
            playRuntime.applyCursorMode(client);
        }

        if (overlayOpen && !camera.isCapturing()) {
            long windowHandle = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    private void handleOverlayState() {
        if (pendingOverlayDispose && overlay != null) {
            pendingOverlayDispose = false;
            if (GLFW.glfwGetCurrentContext() != 0L) {
                overlay.close();
            }
            overlay = null;
            editorContext.setOverlay(null);
        }

        boolean isConnected = session != null && session.state() == SessionState.CONNECTED;
        if (!overlayOpen || !isConnected) {
            return;
        }

        if (overlay == null) {
            overlay = new EditorOverlay(assets);
            overlay.setOpen(true);
            applyBufferedStateToOverlay();
            overlay.requestSnapshot(session);
            editorContext.setOverlay(overlay);
            return;
        }

        if (!overlay.isOpen()) {
            overlay.setOpen(true);
            applyBufferedStateToOverlay();
            overlay.requestSnapshot(session);
            editorContext.setOverlay(overlay);
        }
    }

    private void tickSystems() {
        if (overlayOpen && session != null && session.state() == SessionState.CONNECTED) {
            assets.tick(session);
        }
        sceneAudio.tick(MinecraftClient.getInstance(), playRuntime.isActive() && session != null && session.state() == SessionState.CONNECTED);
        if (playRuntime.isActive() && session != null) {
            playRuntime.tick(session);
            uiInputTracker.tick(session, playRuntime);
        }
        if (session != null) {
            session.tick();
        }
    }

    private void openEditorOverlay(MinecraftClient client, boolean showMessageIfDisconnected) {
        if (overlayOpen) {
            return;
        }

        if (!ClientPlayNetworking.canSend(EnginePayload.ID)) {
            if (showMessageIfDisconnected && client != null && client.inGameHud != null) {
                client.inGameHud.setOverlayMessage(Text.literal("MOUD editor: connect to a MOUD server"), false);
            }
            return;
        }

        overlayOpen = true;
        pendingRestoreSnapshot = true;
        camera.setEnabled(true);

        if (client != null && client.mouse != null) {
            client.mouse.unlockCursor();
        }

        if (session != null && session.state() == SessionState.CONNECTED) {
            session.send(Lane.STATE, new EditorModeChanged(true));
            lastEditorModeSent = true;
        }

        if (overlay != null && session != null && session.state() == SessionState.CONNECTED) {
            overlay.setOpen(true);
            overlay.requestSnapshot(session);
        }

        editorContext.setOverlay(overlay);
    }

    private void closeEditorOverlay(MinecraftClient client) {
        overlayOpen = false;
        camera.setEnabled(false);
        MinecraftGhostBlocks.get().cancel();

        if (overlay != null) {
            overlay.setOpen(false);
        }

        if (client != null && client.currentScreen == null && client.mouse != null) {
            client.mouse.lockCursor();
        }

        editorContext.setOverlay(overlay);

        if (overlay != null) {
            overlay.saveAllOpenEditors();
        }

        if (session != null && session.state() == SessionState.CONNECTED) {
            session.send(Lane.STATE, new EditorModeChanged(false));
            lastEditorModeSent = false;
            session.send(Lane.EVENTS, new RequestRespawn());
            session.send(Lane.STATE, new SceneSnapshotRequest(nextSceneSnapshotRequestId++));
        }
    }

    private static void blockVanillaInput(MinecraftClient client) {
        if (client == null) {
            return;
        }

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

    private void onMessage(Lane lane, Message message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && !client.isOnThread()) {
            client.execute(() -> onMessage(lane, message));
            return;
        }

        if (ClientDebugLog.enabled() && message != null) {
            ClientDebugLog.debug("recv lane=" + lane + " type=" + message.type());
        }

        if (lane == Lane.ASSETS) {
            assets.onMessage(message);
            return;
        }

        handleEngineMessage(message);
    }

    private void handleEngineMessage(Message message) {
        boolean overlayReady = overlay != null && overlay.isOpen();

        if (message instanceof RuntimeState state) {
            playRuntime.onRuntimeState(state);
        } else if (message instanceof CursorState state) {
            playRuntime.onCursorState(state);
        } else if (message instanceof ProjectInfo info && overlayReady) {
            overlay.onProjectInfo(info);
        } else if (message instanceof ProjectCreateAck ack && overlayReady) {
            overlay.onProjectCreateAck(ack);
        } else if (message instanceof ScriptActionListResponse response && overlayReady) {
            overlay.onScriptActionListResponse(response);
        } else if (message instanceof ScriptActionInvokeAck ack && overlayReady) {
            overlay.onScriptActionInvokeAck(ack);
        } else if (message instanceof ScriptFileReadResponse response && overlayReady) {
            overlay.onScriptFileReadResponse(response);
        } else if (message instanceof ScriptFileWriteAck ack && overlayReady) {
            overlay.onScriptFileWriteAck(ack);
        } else if (message instanceof SceneSaveAck ack) {
            handleSceneSave(ack, overlayReady);
        } else if (message instanceof SceneCreateAck ack) {
            handleSceneCreate(ack, overlayReady);
        } else if (message instanceof SceneDeleteAck ack) {
            handleSceneDelete(ack, overlayReady);
        } else if (message instanceof SceneSnapshot snapshot) {
            lastSnapshot = snapshot;
            if (pendingRestoreSnapshot) {
                pendingRestoreSnapshot = false;
                ClientSceneBus.markRestorePending();
            }
            ClientSceneBus.applySnapshot(snapshot);
            if (!overlayOpen && playRuntime.isActive() && !pendingRuntimeOps.isEmpty()) {
                ClientSceneBus.applyOps(List.copyOf(pendingRuntimeOps));
                pendingRuntimeOps.clear();
            }
            if (overlay != null) {
                overlay.onSnapshot(snapshot);
            }
        } else if (message instanceof SceneOpBatch batch) {
            if (batch.ops() == null || batch.ops().isEmpty()) {
                return;
            }
            if (lastSnapshot == null) {
                pendingRuntimeOps.addAll(batch.ops());
                if (pendingRuntimeOps.size() > MAX_PENDING_RUNTIME_OPS) {
                    int keepFrom = Math.max(0, pendingRuntimeOps.size() - MAX_PENDING_RUNTIME_OPS);
                    pendingRuntimeOps.subList(0, keepFrom).clear();
                }
                return;
            }
            if (playRuntime.isActive() && !overlayOpen) {
                boolean isPhysics = (batch.batchId() & (1L << 62)) != 0L;
                if (isPhysics) {
                    ClientSceneBus.applyPhysicsOps(batch.ops());
                } else {
                    ClientSceneBus.applyOps(batch.ops());
                }
            }
        } else if (message instanceof SchemaSnapshot schema) {
            lastSchema = schema;
            if (overlay != null) {
                overlay.onSchema(schema);
            }
        } else if (message instanceof SceneList list) {
            lastSceneList = list;
            if (overlay != null) {
                overlay.onSceneList(list);
            }
        } else if (message instanceof SceneOpAck ack) {
            if (overlay != null) {
                overlay.onAck(ack);
            }
            MinecraftGhostBlocks.get().onAck(ack);
        } else if (message instanceof MultiMeshData mmData) {
            InstanceDataStore.accumulate(mmData.nodeId(), mmData.offset(), mmData.total(), mmData.data());
        } else if (message instanceof PlayerMotion motion) {
            ClientPlayerMotionController.onPlayerMotion(motion);
        }
    }

    private void handleSceneSave(SceneSaveAck ack, boolean overlayReady) {
        if (overlayReady) {
            overlay.onSceneSaveAck(ack);
            return;
        }
        showHudMessage(ack.success(), "Saved scene: " + ack.sceneId(), "Save failed (" + ack.sceneId() + "): ", ack.error());
    }

    private void handleSceneCreate(SceneCreateAck ack, boolean overlayReady) {
        if (overlayReady) {
            overlay.onSceneCreateAck(ack);
            return;
        }
        showHudMessage(ack.success(), "Created scene: " + ack.sceneId(), "Create failed (" + ack.sceneId() + "): ", ack.error());
    }

    private void handleSceneDelete(SceneDeleteAck ack, boolean overlayReady) {
        if (overlayReady) {
            overlay.onSceneDeleteAck(ack);
            return;
        }
        showHudMessage(ack.success(), "Deleted scene: " + ack.sceneId(), "Delete failed (" + ack.sceneId() + "): ", ack.error());
    }

    private void showHudMessage(boolean success, String successMsg, String errorPrefix, String error) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            String msg = success ? successMsg : errorPrefix + (error == null ? "Unknown error" : error);
            client.inGameHud.setOverlayMessage(Text.literal(msg), false);
        }
    }

    private void applyBufferedStateToOverlay() {
        if (overlay == null) {
            return;
        }

        if (lastSchema != null) {
            overlay.onSchema(lastSchema);
        }
        if (lastSceneList != null) {
            overlay.onSceneList(lastSceneList);
        }
        if (lastSnapshot != null) {
            overlay.onSnapshot(lastSnapshot);
        }
    }
}
