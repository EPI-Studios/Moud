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

        // the hand is the game's own for now, because ours does not exist: it is a separate path
        // from the body renderer and nothing has been drawn down it. with the switch down there
        // was no hand at all in first person, which is further from the game than borrowing its
        // one. it comes down again the day a character's own arm is drawn there
        features.set(Feature.HAND, true);

        // a name over a body is the game's own, drawn from the game's own state. ours would be a
        // ui layer that does not exist yet
        features.set(Feature.NAME_TAGS, true);

        // off, so a body casts the shadow its own shape makes rather than dropping a flat circle
        // under itself. a place that wants the game's circle back turns this on, and the body
        // stops casting its own
        features.set(Feature.BLOB_SHADOWS, false);

        // without it escape does nothing and there is no way out of the client yet
        features.set(Feature.PAUSE_MENU, true);
    }
}
