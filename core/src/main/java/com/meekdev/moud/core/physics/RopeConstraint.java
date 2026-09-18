package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;

public final class RopeConstraint extends Constraint {

    @Prop(min = 0) public double length = 5;

    @Prop(min = 0.01) public double thickness = 0.1;

    public boolean winchEnabled;

    @Prop(min = 0) public double winchTarget = 5;

    @Prop(min = 0) public double winchSpeed = 2;

    @Prop(min = 0) public double winchForce = 10000;

    @Prop(min = 0, max = 200) public double winchResponsiveness = 45;

    public double currentDistance;
}
