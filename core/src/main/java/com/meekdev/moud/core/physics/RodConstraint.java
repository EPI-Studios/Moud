package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;

public final class RodConstraint extends Constraint {

    @Prop(min = 0) public double length = 5;

    @Prop(min = 0.01) public double thickness = 0.1;

    public double currentDistance;
}
