package com.moud.client.fabric;

import com.moud.client.fabric.assets.AssetsClient;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlay;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.util.AssetImportUtil;
import com.moud.client.fabric.net.ClientSessionBus;
import com.moud.client.fabric.net.EnginePayload;
import com.moud.client.fabric.net.FabricEngineTransport;
import com.moud.client.fabric.platform.MinecraftFreeflyCamera;
import com.moud.client.fabric.platform.MinecraftGhostBlocks;
import com.moud.client.fabric.model.ModelCache;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
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
import com.moud.net.protocol.SceneCreateAck;
import com.moud.net.protocol.SceneDeleteAck;
import com.moud.net.protocol.SceneList;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneSaveAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SceneSnapshotRequest;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.ScriptActionInvokeAck;
import com.moud.net.protocol.ScriptActionListResponse;
import com.moud.net.protocol.ScriptFileReadResponse;
import com.moud.net.protocol.ScriptFileWriteAck;
import com.moud.net.protocol.ServerHello;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;

public final class FabricClientEntrypoint implements ClientModInitializer {
    private static FabricClientEntrypoint instance;

    private final MinecraftFreeflyCamera camera = new MinecraftFreeflyCamera();
    private final EditorContext editorContext = new EditorContext(camera);
    private final AssetsClient assets = new AssetsClient();
    private final PlayRuntimeClient playRuntime = new PlayRuntimeClient();
    private FabricEngineTransport transport;
    private Session session;
    private EditorOverlay overlay;
    private boolean overlayOpen;
    private boolean pendingOverlayDispose;
    private boolean autoOpenedEditor;
    private KeyBinding toggleKey;
    private boolean dropCallbackRegistered;

    public static EditorOverlay editorOverlay() {
        return instance != null ? instance.overlay : null;
    }
    private volatile SchemaSnapshot lastSchema;
    private volatile SceneList lastSceneList;
    private volatile SceneSnapshot lastSnapshot;
    private long nextSceneSnapshotRequestId = 1L;
    private boolean initialSnapshotRequested;
    private boolean initialManifestRequested;

    @Override
    public void onInitializeClient() {
        instance = this;
        PayloadTypeRegistry.playS2C().register(EnginePayload.ID, EnginePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EnginePayload.ID, EnginePayload.CODEC);

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.editor",
                GLFW.GLFW_KEY_F8,
                "category.moud"
        ));

        ClientTickEvents.END_WORLD_TICK.register(world -> {
            if (dropCallbackRegistered) {
                return;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return;
            }
            dropCallbackRegistered = true;
            long handle = mc.getWindow().getHandle();
            GLFW.glfwSetDropCallback(handle, (window, count, names) -> {
                for (int i = 0; i < count; i++) {
                    String path = GLFWDropCallback.getName(names, i);
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    EditorContext ctx = EditorOverlayBus.get();
                    if (ctx == null || !ctx.isActive()) {
                        continue;
                    }
                    EditorOverlay ov = ctx.overlay();
                    EditorRuntime rt = ov != null ? ov.getRuntime() : null;
                    if (rt == null) {
                        continue;
                    }
                    String finalPath = path;
                    Thread.ofVirtual().start(() -> AssetImportUtil.importDroppedFile(rt, finalPath));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EnginePayload.ID, (payload, context) -> {
            FabricEngineTransport t = transport;
            if (t == null) {
                return;
            }
            context.client().execute(() -> t.acceptServerPayload(payload.data()));
        });

        EditorOverlayBus.set(editorContext);
        PlayRuntimeBus.set(playRuntime);
        VeilSceneNodeRenderer.init();
        VeilWorldEnvironmentRenderer.init();
        MoudTextures.init(assets);
        MoudTextAssets.init(assets);
        ModelCache.init(assets);
        initIcons();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(this::onJoin));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::onDisconnect));

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            Session s = session;
            boolean connected = s != null && s.state() == SessionState.CONNECTED;
            if (connected && overlayOpen && overlay != null) {
                try {
                    overlay.render(s);
                } catch (Throwable t) {
                    ClientDebugLog.error("EditorOverlay.render crashed", t);
                }
            }

            if (playRuntime.isActive() && connected) {
                playRuntime.tick(s);
            }
        });
    }

    private static void initIcons() {
        // Register editor icons from assets/moud/icons/*.png
        // Add entries here as you drop PNGs into that folder:
        // MoudIcons.loadFromResource("node",    "/assets/moud/icons/node.png");
        // MoudIcons.loadFromResource("camera",  "/assets/moud/icons/camera.png");
        // MoudIcons.loadFromResource("light",   "/assets/moud/icons/light.png");
    }

    private void onJoin() {
        transport = null;
        session = null;
        ClientSessionBus.set(null);
        overlayOpen = false;
        lastSchema = null;
        lastSceneList = null;
        lastSnapshot = null;
        ClientSceneBus.clear();
        VeilSceneNodeRenderer.clearLights();
        VeilWorldEnvironmentRenderer.clear();
        MoudTextures.clear();
        MoudTextAssets.clear();
        ModelCache.clear();
        nextSceneSnapshotRequestId = 1L;
        initialSnapshotRequested = false;
        initialManifestRequested = false;
        autoOpenedEditor = false;
        camera.setEnabled(false);
        camera.resetBootstrap();
        MinecraftGhostBlocks.get().cancel();
        playRuntime.onDisconnect();
        if (overlay != null) {
            overlay.setOpen(false);
        }
        editorContext.setOverlay(overlay);
    }

    private void onDisconnect() {
        transport = null;
        session = null;
        ClientSessionBus.set(null);
        overlayOpen = false;
        lastSchema = null;
        lastSceneList = null;
        lastSnapshot = null;
        ClientSceneBus.clear();
        VeilSceneNodeRenderer.clearLights();
        VeilWorldEnvironmentRenderer.clear();
        MoudTextures.clear();
        MoudTextAssets.clear();
        ModelCache.clear();
        nextSceneSnapshotRequestId = 1L;
        initialSnapshotRequested = false;
        initialManifestRequested = false;
        autoOpenedEditor = false;
        camera.setEnabled(false);
        camera.resetBootstrap();
        MinecraftGhostBlocks.get().cancel();
        playRuntime.onDisconnect();
        if (overlay != null) {
            overlay.setOpen(false);
            pendingOverlayDispose = true;
        }
        editorContext.setOverlay(overlay);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen == null) {
            client.mouse.lockCursor();
        }
    }

    private void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        MinecraftGhostBlocks.get().clientTick();
        Session currentSession = session;
        boolean connected = currentSession != null && currentSession.state() == SessionState.CONNECTED;
        if (connected && !initialSnapshotRequested && lastSnapshot == null) {
            initialSnapshotRequested = true;
            currentSession.send(Lane.STATE, new SceneSnapshotRequest(nextSceneSnapshotRequestId++));
        }
        if (connected && !initialManifestRequested) {
            initialManifestRequested = true;
            assets.requestManifest(currentSession);
        }
        if (connected && !autoOpenedEditor) {
            ServerHello hello = currentSession.serverHello();
            if (hello != null) {
                autoOpenedEditor = true;
                if (hello.devMode() && !overlayOpen) {
                    openEditorOverlay(client, false);
                }
            }
        }
        playRuntime.setActive(connected && !overlayOpen);
        if (pendingOverlayDispose && overlay != null) {
            if (GLFW.glfwGetCurrentContext() != 0L) {
                pendingOverlayDispose = false;
                overlay.close();
                overlay = null;
            } else {
                pendingOverlayDispose = false;
                overlay = null;
            }
            editorContext.setOverlay(null);
        }
        while (toggleKey != null && toggleKey.wasPressed()) {
            if (!overlayOpen) {
                openEditorOverlay(client, true);
            } else {
                closeEditorOverlay(client);
            }
        }
        if (transport == null && session == null && ClientPlayNetworking.canSend(EnginePayload.ID)) {
            transport = new FabricEngineTransport();
            session = new Session(SessionRole.CLIENT, transport);
            ClientSessionBus.set(session);
            session.setLogSink(s -> System.out.println("[moud-client] " + s));
            session.setMessageHandler(this::onMessage);
            session.start();
        }
        if ((overlayOpen || playRuntime.shouldBlockVanillaInput(client)) && client.currentScreen == null) {
            blockVanillaInput(client);
        }
        if (playRuntime.isActive() && client.currentScreen == null) {
            client.mouse.lockCursor();
        }
        if (overlayOpen && !camera.isCapturing()) {
            long handle = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        if (overlayOpen && overlay == null && session != null && session.state() == SessionState.CONNECTED) {
            overlay = new EditorOverlay(assets);
            overlay.setOpen(true);
            applyBufferedStateToOverlay();
            overlay.requestSnapshot(session);
            editorContext.setOverlay(overlay);
        } else if (overlayOpen && overlay != null && !overlay.isOpen() && session != null && session.state() == SessionState.CONNECTED) {
            overlay.setOpen(true);
            applyBufferedStateToOverlay();
            overlay.requestSnapshot(session);
            editorContext.setOverlay(overlay);
        }
        if (overlayOpen && session != null && session.state() == SessionState.CONNECTED) {
            assets.tick(session);
        }
        if (playRuntime.isActive() && session != null) {
            playRuntime.tick(session);
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
        camera.setEnabled(true);
        if (client != null && client.mouse != null) {
            client.mouse.unlockCursor();
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
        Session s = session;
        if (s != null && s.state() == SessionState.CONNECTED) {
            s.send(Lane.EVENTS, new RequestRespawn());
        }
    }

    private static void blockVanillaInput(MinecraftClient client) {
        if (client == null) {
            return;
        }
        var options = client.options;
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && !mc.isOnThread()) {
            Lane ln = lane;
            Message msg = message;
            mc.execute(() -> onMessage(ln, msg));
            return;
        }
        if (ClientDebugLog.enabled() && message != null) {
            ClientDebugLog.debug("recv lane=" + lane + " type=" + message.type());
        }
        if (lane == Lane.ASSETS) {
            assets.onMessage(message);
            return;
        }
        if (message instanceof RuntimeState state) {
            playRuntime.onRuntimeState(state);
            return;
        }
        if (message instanceof ProjectInfo info) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onProjectInfo(info);
            }
            return;
        }
        if (message instanceof ProjectCreateAck ack) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onProjectCreateAck(ack);
            }
            return;
        }
        if (message instanceof ScriptActionListResponse response) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onScriptActionListResponse(response);
            }
            return;
        }
        if (message instanceof ScriptActionInvokeAck ack) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onScriptActionInvokeAck(ack);
            }
            return;
        }
        if (message instanceof ScriptFileReadResponse response) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onScriptFileReadResponse(response);
            }
            return;
        }
        if (message instanceof ScriptFileWriteAck ack) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onScriptFileWriteAck(ack);
            }
            return;
        }
        if (message instanceof SceneSaveAck ack) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onSceneSaveAck(ack);
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.inGameHud != null) {
                String msg = ack.success()
                        ? "Saved scene: " + ack.sceneId()
                        : "Save failed (" + ack.sceneId() + "): " + (ack.error() == null ? "Unknown error" : ack.error());
                client.inGameHud.setOverlayMessage(Text.literal(msg), false);
            }
            return;
        }
        if (message instanceof SceneCreateAck ack) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onSceneCreateAck(ack);
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.inGameHud != null) {
                String msg = ack.success()
                        ? "Created scene: " + ack.sceneId()
                        : "Create failed (" + ack.sceneId() + "): " + (ack.error() == null ? "Unknown error" : ack.error());
                client.inGameHud.setOverlayMessage(Text.literal(msg), false);
            }
            return;
        }
        if (message instanceof SceneDeleteAck ack) {
            if (overlay != null && overlay.isOpen()) {
                overlay.onSceneDeleteAck(ack);
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.inGameHud != null) {
                String msg = ack.success()
                        ? "Deleted scene: " + ack.sceneId()
                        : "Delete failed (" + ack.sceneId() + "): " + (ack.error() == null ? "Unknown error" : ack.error());
                client.inGameHud.setOverlayMessage(Text.literal(msg), false);
            }
            return;
        }
        if (message instanceof SceneSnapshot snapshot) {
            lastSnapshot = snapshot;
            ClientSceneBus.applySnapshot(snapshot);
            if (overlay != null) {
                overlay.onSnapshot(snapshot);
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
        }
    }

    private void applyBufferedStateToOverlay() {
        EditorOverlay ov = overlay;
        if (ov == null) {
            return;
        }
        SchemaSnapshot schema = lastSchema;
        if (schema != null) {
            ov.onSchema(schema);
        }
        SceneList list = lastSceneList;
        if (list != null) {
            ov.onSceneList(list);
        }
        SceneSnapshot snapshot = lastSnapshot;
        if (snapshot != null) {
            ov.onSnapshot(snapshot);
        }
    }
}
