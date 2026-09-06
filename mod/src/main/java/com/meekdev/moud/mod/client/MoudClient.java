package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Parts;
import net.fabricmc.api.ClientModInitializer;

public final class MoudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientScene.start();
        if (Demo.enabled()) {
            Demo.switches(MoudMod.features());
            Demo.build(ClientScene.world());
        }
        Parts.register();
        new Launch(MoudMod.features()).install();
        MoudMod.LOG.info("moud client ready");
    }
}
