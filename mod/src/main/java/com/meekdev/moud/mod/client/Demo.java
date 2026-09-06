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

    // the floor is the place's own, from polar, so the parts only have to sit on top of it
    static void build(Instance world) {
        Instance grid = Instances.create(Classes.FOLDER, world, "grid");
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int gx = x;
                int gz = z;
                Instances.create(Classes.PART, grid, "p" + x + "_" + z, p -> {
                    p.cframe = CFrame.at(-31 + gx * 2, 62 + ((gx + gz) % 3), -31 + gz * 2);
                    p.size = new Vec3(1.4, 1.4, 1.4);
                    p.color = new Color(0.2f + gx / 48f, 0.35f, 0.7f - gz / 64f);
                });
            }
        }
    }
}
