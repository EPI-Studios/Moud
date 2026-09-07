package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.Switches;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.adapter.render.Pipeline;
import com.meekdev.moud.mod.client.editor.Editor;
import net.fabricmc.api.ClientModInitializer;
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
    // drained here and nowhere else, which is the one point design 7.4 puts it at
    private static void frames() {
        Clock frame = new Clock();
        Diagnostics diagnostics = new Diagnostics();
        LevelRenderEvents.START_MAIN.register(context -> {
            double dt = frame.tick();
            ClientScene.frame(dt);
            diagnostics.tick(dt);
            if (ClientScene.motion().takeStillChanged()) Parts.invalidateStill();
        });
    }
}
