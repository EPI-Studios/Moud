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
        if (vm == null && Demo.enabled()) {
            Demo.switches(MoudMod.features());
            Demo.build(ClientScene.world());
        } else if (vm != null) {
            Demo.switches(MoudMod.features());
            step(vm);
        }
        Parts.register();
        new Launch(MoudMod.features()).install();
        MoudMod.LOG.info("moud client ready");
    }

    // stepped is the tick, renderStepped is the frame, and both get real time rather than a rate
    private static void step(Vm vm) {
        Clock tick = new Clock();
        Clock frame = new Clock();
        ClientTickEvents.END_CLIENT_TICK.register(client -> vm.step(tick.tick()));
        LevelRenderEvents.START_MAIN.register(context -> vm.renderStep(frame.tick()));
    }
}
