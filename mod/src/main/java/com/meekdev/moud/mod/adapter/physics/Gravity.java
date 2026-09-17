package com.meekdev.moud.mod.adapter.physics;

import com.meekdev.bkun.box3d.LevelPhysics;
import com.meekdev.box3d.Vec3;
import org.jspecify.annotations.Nullable;

final class Gravity {

    static final double DEFAULT = 32;

    private static double value = DEFAULT;

    private Gravity() {}

    static double value() {
        return value;
    }

    static double scale() {
        return value / DEFAULT;
    }

    static void set(double metresPerSecondSquared, @Nullable LevelPhysics physics) {
        if (!Double.isFinite(metresPerSecondSquared)) throw new IllegalArgumentException("gravity must be a finite number");
        value = metresPerSecondSquared;
        if (physics != null) physics.world().setGravity(new Vec3(0, -value, 0));
    }
}
