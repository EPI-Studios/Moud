package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.place.Scripts;
import com.meekdev.moud.script.vm.Vm;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

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

    private static void step(Vm vm) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> vm.step(1.0 / 20.0));
    }
}
