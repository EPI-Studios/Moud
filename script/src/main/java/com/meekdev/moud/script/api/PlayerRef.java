package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;

public interface PlayerRef {

    String name();

    String id();

    Instance character();

    void spawn(Vector3 position);

    ControlsRef controls();

    default Instance team() {
        return null;
    }

    default void team(Instance team) {
    }

    default Instance leaderstats() {
        return null;
    }

    void kick(String message);

    default void ban(String reason, double seconds) {
        kick(reason);
    }

    double ping();

    double viewTime();
}
