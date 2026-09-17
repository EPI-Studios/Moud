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

    void kick(String message);

    double ping();

    double viewTime();
}
