package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.place.Switches;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.adapter.render.Pipeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class MoudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Switches.install(MoudMod.features());
        Pipeline.install();
        Physics.install();
        Parts.register();
        frames();
        new Launch(MoudMod.features()).install();
        MoudMod.LOG.info("moud client ready");
    }

    // the client does not run the place, it draws what the server says exists. the mirror is
    // drained here and nowhere else, which is the one point design 7.4 puts it at
    private static void frames() {
        Clock frame = new Clock();
        LevelRenderEvents.START_MAIN.register(context -> {
            double dt = frame.tick();
            ClientScene.frame(dt);
            if (ClientScene.motion().takeStillChanged()) Parts.invalidateStill();
        });
    }
}
