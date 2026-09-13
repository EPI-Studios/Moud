package com.meekdev.moud.script.api;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vec3;

public interface PlayerRef {

    String name();

    Instance character();

    void spawn(Vec3 position);

    default double ping() {
        return 0;
    }

    default double viewTime() {
        return 0;
    }
}
