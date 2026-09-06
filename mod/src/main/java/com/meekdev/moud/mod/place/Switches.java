package com.meekdev.moud.mod.place;

import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;

// stands in for place.toml: a place declares what it needs on, nothing is on by default
public final class Switches {

    private Switches() {}

    public static void install(Features features) {
        features.set(Feature.TERRAIN, true);
        features.set(Feature.VANILLA_MOVEMENT, true);
        // without it escape does nothing and there is no way out of the client yet
        features.set(Feature.PAUSE_MENU, true);
    }
}
