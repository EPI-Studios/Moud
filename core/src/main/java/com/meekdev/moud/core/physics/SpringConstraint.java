package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;

public final class SpringConstraint extends Constraint {

    @Prop(min = 0) public double freeLength = 2;
    @Prop(min = 0) public double stiffness = 50;
    @Prop(min = 0) public double damping = 2;
    public boolean limitsEnabled;
    @Prop(min = 0) public double minLength;
    @Prop(min = 0) public double maxLength = 5;

    @Prop(driven = true) public double currentLength;
}
