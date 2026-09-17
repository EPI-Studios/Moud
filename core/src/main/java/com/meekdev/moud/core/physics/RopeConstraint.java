package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;

public final class RopeConstraint extends Constraint {

    @Prop(min = 0) public double length = 5;

    @Prop(driven = true) public double currentDistance;
}
