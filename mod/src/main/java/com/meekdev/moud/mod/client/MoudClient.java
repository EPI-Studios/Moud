package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.place.Scripts;
import com.meekdev.moud.script.vm.Vm;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class MoudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientScene.start();
        Vm vm = Scripts.run(ClientScene.world(), Classes.registry());
        if (vm != null || Demo.enabled()) Demo.switches(MoudMod.features());
        if (vm == null && Demo.enabled()) Demo.build(ClientScene.world());

        Parts.register();
        frames(vm);
        new Launch(MoudMod.features()).install();
        MoudMod.LOG.info("moud client ready");
    }

    // stepped is the tick, renderStepped is the frame, and both get real time rather than a rate.
    // the drain runs every frame whether or not a script does, because interpolation is what the
    // renderer samples from
    private static void frames(Vm vm) {
        Clock tick = new Clock();
        Clock frame = new Clock();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (vm != null) vm.step(tick.tick());
        });
        LevelRenderEvents.START_MAIN.register(context -> {
            double dt = frame.tick();
            if (vm != null) vm.renderStep(dt);
            ClientScene.motion().drain(ClientScene.tree(), dt);
        });
    }
}
