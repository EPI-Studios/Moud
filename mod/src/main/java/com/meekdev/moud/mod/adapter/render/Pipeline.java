package com.meekdev.moud.mod.adapter.render;

import com.meekdev.amnetic.client.taa.Taa;

// engine wide amnetic settings, so nothing outside the render adapter reaches into the library
public final class Pipeline {

    private Pipeline() {}

    public static void install() {
        // temporal aa smears anything that moves, and a place full of moving parts is the case
        // it is worst at. a place that wants it back turns it on
        Taa.disable();
    }
}
