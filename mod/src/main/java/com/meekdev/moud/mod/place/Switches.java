package com.meekdev.moud.mod.place;

import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;

// stands in for place.toml: a place declares what it needs on, nothing is on by default
public final class Switches {

    private Switches() {}

    public static void install(Features features) {
        features.set(Feature.TERRAIN, true);
        // with it off the dimension's ambient is forced to zero, so anything the sun does not
        // reach is pure black and reads as missing rather than dark
        features.set(Feature.AMBIENT_LIGHT, true);
        features.set(Feature.VANILLA_MOVEMENT, true);
        // the player model stays down now that a character has a body of its own. it was up while
        // there was nothing else to see yourself as, and with it up the vanilla body was drawn
        // straight through ours -- which reads as our renderer being broken rather than as a
        // switch nobody lowered

        // without it escape does nothing and there is no way out of the client yet
        features.set(Feature.PAUSE_MENU, true);
    }
}
