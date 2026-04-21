package com.moud.client.fabric;

import com.moud.client.fabric.assets.MoudAudioAssets;
import com.moud.client.fabric.assets.MoudTextAssets;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.util.AssetImportUtil;
import com.moud.client.fabric.input.ClientInputMap;
import com.moud.client.fabric.model.ModelCache;
import com.moud.client.fabric.net.EnginePayload;
import com.moud.client.fabric.player.MoudPalAnimLayer;
import com.moud.client.fabric.player.PalAnimInjector;
import com.moud.client.fabric.render.MoudIcons;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.client.fabric.render.VeilSceneNodeRenderer;
import com.moud.client.fabric.render.env.VeilWorldEnvironmentRenderer;
import com.moud.client.fabric.runtime.PlayRuntimeBus;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;

final class MoudClientBootstrap {
    private final MoudClientContext ctx;

    MoudClientBootstrap(MoudClientContext ctx) {
        this.ctx = ctx;
    }

    void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(EnginePayload.ID, EnginePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EnginePayload.ID, EnginePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(EnginePayload.ID, (payload, context) -> {
            if (ctx.transport != null) {
                context.client().execute(() -> ctx.transport.acceptServerPayload(payload.data()));
            }
        });
    }

    void registerKeybindings() {
        ctx.toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.editor", GLFW.GLFW_KEY_F8, "category.moud"));
        ctx.collisionDebugKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.collision_debug", GLFW.GLFW_KEY_F9, "category.moud"));
        ctx.viewportPlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.moud.viewport_play", GLFW.GLFW_KEY_F7, "category.moud"));
    }

    void initializeSubsystems() {
        EditorOverlayBus.set(ctx.editorContext);
        PlayRuntimeBus.set(ctx.playRuntime);
        VeilSceneNodeRenderer.init();
        VeilWorldEnvironmentRenderer.init();
        MoudPalAnimLayer.register();
        ctx.assets.addListener(new PalAnimInjector(ctx.assets));
        MoudTextures.init(ctx.assets);
        MoudTextAssets.init(ctx.assets);
        MoudAudioAssets.init(ctx.assets);
        ModelCache.init(ctx.assets);
        ClientInputMap.ensureRegistered();
        ctx.uriServer.start();
    }

    void registerFileDropCallback() {
        if (ctx.dropCallbackRegistered) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        ctx.dropCallbackRegistered = true;
        long windowHandle = client.getWindow().getHandle();

        GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
            EditorContext context = EditorOverlayBus.get();
            if (context == null || !context.isActive() || context.overlay() == null) return;

            EditorRuntime runtime = context.overlay().getRuntime();
            if (runtime == null) return;

            for (int i = 0; i < count; i++) {
                String path = GLFWDropCallback.getName(names, i);
                if (path != null && !path.isBlank()) {
                    Thread.ofVirtual().start(() -> AssetImportUtil.importDroppedFile(runtime, path));
                }
            }
        });
    }

    static void initIcons() {
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
}
