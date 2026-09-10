package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.Switches;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.adapter.render.Skins;
import com.meekdev.moud.mod.adapter.render.Pipeline;
import com.meekdev.moud.mod.client.editor.Editor;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class MoudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Switches.install(MoudMod.features());
        Pipeline.install();
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
        LevelRenderEvents.START_MAIN.register(context -> {
            ClientScene.frame();
            float partialTick = Minecraft.getInstance().getDeltaTracker()
                    .getGameTimeDeltaPartialTick(true);
            ClientPlace.frame(partialTick);
            Skins.gather(partialTick);
            if (ClientScene.motion().takeStillChanged()) Parts.invalidateStill();
        });
    }
}
