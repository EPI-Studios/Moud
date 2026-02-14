package com.moud.client.fabric;

import com.moud.client.fabric.net.FabricEngineTransport;
import com.moud.client.fabric.net.EnginePayload;
import com.moud.client.fabric.editor.camera.EditorFreeflyCamera;
import com.moud.client.fabric.editor.ghost.EditorGhostBlocks;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlay;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.net.protocol.Message;
import com.moud.net.protocol.SceneOpAck;
import com.moud.net.protocol.SceneSnapshot;
import com.moud.net.protocol.SchemaSnapshot;
import com.moud.net.protocol.SceneList;
import com.moud.net.session.Session;
import com.moud.net.session.SessionRole;
import com.moud.net.session.SessionState;
import com.moud.net.transport.Lane;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class FabricClientEntrypoint implements ClientModInitializer {
    private final EditorFreeflyCamera camera = new EditorFreeflyCamera();
    private final EditorContext editorContext = new EditorContext(camera);
    private FabricEngineTransport transport;
    private Session session;
    private EditorOverlay overlay;
    private boolean overlayOpen;
    private boolean pendingOverlayDispose;
    private KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(EnginePayload.ID, EnginePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EnginePayload.ID, EnginePayload.CODEC);

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.editor",
                GLFW.GLFW_KEY_F8,
                "category.moud"
        ));

        ClientPlayNetworking.registerGlobalReceiver(EnginePayload.ID, (payload, context) -> {
            FabricEngineTransport t = transport;
            if (t == null) {
                return;
            }
            context.client().execute(() -> t.acceptServerPayload(payload.data()));
        });

        EditorOverlayBus.set(editorContext);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (session != null && session.state() == SessionState.CONNECTED && overlayOpen && overlay != null) {
                overlay.render(session);
            }
        });
    }

    private void onJoin() {
        transport = null;
        session = null;
        overlayOpen = false;
        camera.setEnabled(false);
        EditorGhostBlocks.get().cancel();
        if (overlay != null) {
            overlay.setOpen(false);
        }
        editorContext.setOverlay(overlay);
    }

    private void onDisconnect() {
        transport = null;
        session = null;
        overlayOpen = false;
        camera.setEnabled(false);
        EditorGhostBlocks.get().cancel();
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
        EditorGhostBlocks.get().clientTick();
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
                if (!ClientPlayNetworking.canSend(EnginePayload.ID)) {
                    client.inGameHud.setOverlayMessage(Text.literal("MOUD editor: connect to a MOUD server"), false);
                    continue;
                }
                overlayOpen = true;
                camera.setEnabled(true);
                client.mouse.unlockCursor();
                if (overlay != null && session != null && session.state() == SessionState.CONNECTED) {
                    overlay.setOpen(true);
                    overlay.requestSnapshot(session);
                }
            } else {
                overlayOpen = false;
                camera.setEnabled(false);
                EditorGhostBlocks.get().cancel();
                if (overlay != null) {
                    overlay.setOpen(false);
                }
                if (client.currentScreen == null) {
                    client.mouse.lockCursor();
                }
            }
            editorContext.setOverlay(overlay);
        }
        if (overlayOpen && transport == null && session == null && ClientPlayNetworking.canSend(EnginePayload.ID)) {
            transport = new FabricEngineTransport();
            session = new Session(SessionRole.CLIENT, transport);
            session.setLogSink(s -> System.out.println("[moud-client] " + s));
            session.setMessageHandler(this::onMessage);
            session.start();
        }
        if (overlayOpen && client.currentScreen == null) {
            blockVanillaInput(client);
        }
        if (overlayOpen && !camera.isCapturing()) {
            long handle = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        if (overlayOpen && overlay == null && session != null && session.state() == SessionState.CONNECTED) {
            overlay = new EditorOverlay();
            overlay.setOpen(true);
            overlay.requestSnapshot(session);
            editorContext.setOverlay(overlay);
        } else if (overlayOpen && overlay != null && !overlay.isOpen() && session != null && session.state() == SessionState.CONNECTED) {
            overlay.setOpen(true);
            overlay.requestSnapshot(session);
            editorContext.setOverlay(overlay);
        }
        if (session != null) {
            session.tick();
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
        if (message instanceof SceneSnapshot snapshot) {
            if (overlay != null) {
                overlay.onSnapshot(snapshot);
            }
        } else if (message instanceof SchemaSnapshot schema) {
            if (overlay != null) {
                overlay.onSchema(schema);
            }
        } else if (message instanceof SceneList list) {
            if (overlay != null) {
                overlay.onSceneList(list);
            }
        } else if (message instanceof SceneOpAck ack) {
            if (overlay != null) {
                overlay.onAck(ack);
            }
            EditorGhostBlocks.get().onAck(ack);
        }
    }
}
