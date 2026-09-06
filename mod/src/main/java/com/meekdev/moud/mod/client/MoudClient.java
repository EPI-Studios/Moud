package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.features.Features;
import net.fabricmc.api.ClientModInitializer;

public final class MoudClient implements ClientModInitializer {

    private static final Features FEATURES = new Features();

    @Override
    public void onInitializeClient() {
        ClientScene.start();
        if (Demo.enabled()) Demo.build(ClientScene.world());
        Parts.register();
        MoudMod.LOG.info("moud client ready");
    }

    public static Features features() {
        return FEATURES;
    }
}
