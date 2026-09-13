package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.taa.Taa;

public final class Pipeline {

    private Pipeline() {}

    public static void install() {
        Taa.disable();
    }
}
