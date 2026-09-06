package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;

// scaffolding until scripts build scenes, run with -Dmoud.demo=true
final class Demo {

    private Demo() {}

    static boolean enabled() {
        return Boolean.getBoolean("moud.demo");
    }

    // stands in for place.toml: a scene declares what it needs on, nothing is on by default
    static void switches(Features features) {
        features.set(Feature.TERRAIN, true);
        features.set(Feature.VANILLA_MOVEMENT, true);
        // without it escape does nothing and there is no way out of the client yet
        features.set(Feature.PAUSE_MENU, true);
    }

}
