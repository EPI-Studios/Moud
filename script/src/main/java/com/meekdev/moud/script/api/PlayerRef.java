package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;

public interface PlayerRef {

    String name();

    Instance character();

    void spawn(Vector3 position);

    default double ping() {
        return 0;
    }

    default double viewTime() {
        return 0;
    }
}
