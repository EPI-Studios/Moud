package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;

public interface PlayerRef {

    String name();

    Instance character();

    void spawn(Vector3 position);

    ControlsRef controls();

    void kick(String message);

    double ping();

    double viewTime();
}
