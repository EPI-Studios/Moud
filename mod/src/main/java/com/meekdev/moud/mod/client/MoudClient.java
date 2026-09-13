package com.meekdev.moud.mod.client;

import net.minecraft.client.player.LocalPlayer;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.adapter.render.Skins;
import com.meekdev.moud.mod.adapter.audio.ResonaAudio;
import com.meekdev.moud.mod.adapter.audio.Sounds;
import com.meekdev.moud.mod.adapter.ui.Ui;
import com.meekdev.moud.mod.adapter.render.Pipeline;
import com.meekdev.moud.mod.client.editor.Editor;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class MoudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Input.register();
        Post.installOnClient(() -> Minecraft.getInstance().player instanceof LocalPlayer me
                ? me.getUUID().toString() : "");
        Pipeline.install();
        ResonaAudio.INSTANCE.install();
        Editor.install();
        Parts.register();
        frames();
        new Launch(MoudMod.features()).install();
        MoudMod.LOG.info("moud client ready");
    }

    // the client does not run the place, it draws what the server says exists. the mirror is
    // drained on the client tick and nowhere else, and the frame only draws what that left
    private static void frames() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientScene.tick();
            ClientPlace.tick();
        });
        // the place itself is stepped earlier, from GameRenderer.update, because the camera it
        // writes has to exist before the world is culled against it. what is left here is the
        // packing, which has to happen as late as possible instead: just before the batches draw
        LevelRenderEvents.START_MAIN.register(context -> {
            ClientScene.frame();
            float partialTick = Minecraft.getInstance().getDeltaTracker()
                    .getGameTimeDeltaPartialTick(true);
            Skins.gather(partialTick);
            Ui.frame(partialTick);
            Sounds.frame(partialTick);
            // every number behind this frame of your own body, while it is standing on something
            // that moves. it writes itself and stops, so there is nothing to turn on
            Trace.frame(partialTick);
            if (ClientScene.motion().takeStillChanged()) Parts.invalidateStill();
        });
    }
}
