package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

// scaffolding until scripts build scenes, run with -Dmoud.demo=true
final class Demo {

    private Demo() {}

    static boolean enabled() {
        return Boolean.getBoolean("moud.demo");
    }

    static void build(Instance world) {
        Instances.create(Classes.PART, world, "floor", p -> {
            p.cframe = CFrame.at(0, 63, 0);
            p.size = new Vec3(64, 1, 64);
            p.color = new Color(0.36f, 0.42f, 0.38f);
        });

        Instance grid = Instances.create(Classes.FOLDER, world, "grid");
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int gx = x;
                int gz = z;
                Instances.create(Classes.PART, grid, "p" + x + "_" + z, p -> {
                    p.cframe = CFrame.at(-31 + gx * 2, 65 + ((gx + gz) % 3), -31 + gz * 2);
                    p.size = new Vec3(1.4, 1.4, 1.4);
                    p.color = new Color(0.2f + gx / 48f, 0.35f, 0.7f - gz / 64f);
                });
            }
        }
    }
}
