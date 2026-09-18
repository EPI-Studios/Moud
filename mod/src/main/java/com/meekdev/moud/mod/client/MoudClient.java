package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.audio.ResonaAudio;
import com.meekdev.moud.mod.adapter.audio.Sounds;
import com.meekdev.moud.mod.adapter.chat.Bubbles;
import com.meekdev.moud.mod.adapter.chat.ChatView;
import com.meekdev.moud.mod.adapter.chat.ClientChat;
import com.meekdev.moud.mod.adapter.java.GameTypes;
import com.meekdev.moud.mod.adapter.render.Meshes;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.adapter.render.Pipeline;
import com.meekdev.moud.mod.adapter.render.EditorOverlay;
import com.meekdev.moud.mod.adapter.render.Environment;
import com.meekdev.moud.mod.adapter.render.ModelSnapshots;
import com.meekdev.moud.mod.adapter.render.PostStack;
import com.meekdev.moud.mod.adapter.render.Viewports;
import com.meekdev.moud.mod.adapter.render.ViewportCapture;
import com.meekdev.moud.mod.adapter.render.SceneLights;
import com.meekdev.moud.mod.adapter.render.ShaderPatches;
import com.meekdev.moud.mod.adapter.render.SkyBox;
import com.meekdev.moud.mod.adapter.render.Skins;
import com.meekdev.moud.mod.adapter.physics.OwnedBodies;
import com.meekdev.moud.mod.adapter.image.ClientImageSources;
import com.meekdev.moud.mod.adapter.image.ImageSources;
import com.meekdev.moud.mod.adapter.render.EditableTextures;
import com.meekdev.moud.mod.adapter.render.effect.Effects;
import com.meekdev.moud.mod.adapter.ui.Ui;
import com.meekdev.moud.mod.client.debug.ClientDebug;
import com.meekdev.moud.mod.client.debug.CollisionView;
import com.meekdev.moud.mod.client.editor.Editor;
import com.meekdev.moud.mod.client.input.Autopilot;
import com.meekdev.moud.mod.client.input.Controls;
import com.meekdev.moud.mod.client.input.Push;
import com.meekdev.moud.mod.client.input.SeatInput;
import com.meekdev.moud.mod.client.input.Input;
import com.meekdev.moud.mod.client.zone.ClientPrompts;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.mod.transport.Post;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class MoudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Input.register();
        ClientChat.listen();
        Autopilot.listen();
        Controls.listen();
        Push.listen();
        ClientWorld.listen();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Autopilot.clear();
            SeatInput.clear();
        });
        ChatView.install();
        ClientDebug.install();
        Post.installOnClient(() -> Minecraft.getInstance().player instanceof LocalPlayer me
                ? me.getUUID().toString() : "");
        Pipeline.install();
        ResonaAudio.INSTANCE.install();
        ImageSources.fontFrom(ClientImageSources.INSTANCE::font);
        if (!Game.standalone()) Editor.install();
        GameTypes.install(ClientScene::tree, id -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getPlayerByUUID(id));
        Parts.register();
        Meshes.register();
        PostStack.register();
        SkyBox.register();
        Viewports.register();
        ModelSnapshots.register();
        EditorOverlay.register();
        ViewportCapture.register();
        Effects.register();
        frames();
        new Launch(MoudMod.features()).install();
        MoudMod.LOG.info("moud client ready");
    }

    private static void frames() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientScene.tick();
            ClientChat.INSTANCE.tick();
            ClientPrompts.tick(0.05);
            ClientPlace.tick();
            CollisionView.tick();
            Input.pointerFrame();
        });
        LevelRenderEvents.START_MAIN.register(context -> {
            ClientScene.frame();
            OwnedBodies.frame(ClientScene.tree(), ClientScene.motion());
            float partialTick = Minecraft.getInstance().getDeltaTracker()
                    .getGameTimeDeltaPartialTick(true);
            Skins.prepareFrame(partialTick);
            CoreGui.INSTANCE.frame();
            Ui.frame(partialTick);
            Sounds.frame(partialTick);
            SceneLights.frame(partialTick);
            Effects.frame();
            EditableTextures.sweep();
            ClientImageSources.INSTANCE.frame();
            Bubbles.frame(partialTick);
            ClientPrompts.frame();
            PostStack.frame();
            Environment.frame();
            ShaderPatches.frame();
            if (ClientScene.motion().consumeStaticChanged()) Parts.invalidateStatic();
        });
    }
}
