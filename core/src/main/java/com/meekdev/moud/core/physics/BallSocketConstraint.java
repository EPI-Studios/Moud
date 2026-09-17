package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;

public final class BallSocketConstraint extends Constraint {

    public boolean limitsEnabled;
    @Prop(min = 0, max = 180) public double upperAngle = 45;
    public boolean twistLimitsEnabled;
    @Prop(min = -180, max = 180) public double twistLowerAngle = -45;
    @Prop(min = -180, max = 180) public double twistUpperAngle = 45;
}
