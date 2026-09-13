package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vec3;

// a connected client, as much of one as a script is allowed to see
//
// script may not know what a player is made of, so the mod hands one of these across and keeps
// the entity to itself
public interface PlayerRef {

    String name();

    Instance character();

    void spawn(Vec3 position);

    // the round trip to this player, in seconds
    default double ping() {
        return 0;
    }

    // the server time this player was looking at, for rewinding a hit to what they saw
    default double viewTime() {
        return 0;
    }
}
