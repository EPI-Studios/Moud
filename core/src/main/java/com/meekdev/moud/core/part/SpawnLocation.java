package com.meekdev.moud.core.part;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public final class SpawnLocation extends Part {

    public boolean enabled = true;

    public SpawnLocation() {
        size = new Vector3(6, 1, 6);
        color = new Color(0.42f, 0.44f, 0.47f, 1f);
    }
}
