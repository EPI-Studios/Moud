package com.meekdev.moud.mod.place;

import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;

public final class Switches {

    private Switches() {}

    public static void install(Features features) {
        features.set(Feature.TERRAIN, true);
        features.set(Feature.AMBIENT_LIGHT, true);
        features.set(Feature.VANILLA_MOVEMENT, true);

        features.set(Feature.HAND, false);

        features.set(Feature.NAME_TAGS, true);

        features.set(Feature.BLOB_SHADOWS, false);

        features.set(Feature.PAUSE_MENU, true);

        features.set(Feature.TRACE, false);
    }
}
