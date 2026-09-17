package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;

public final class PrismaticConstraint extends Constraint {

    public ActuatorType actuatorType = ActuatorType.NONE;
    public double velocity;
    @Prop(min = 0) public double motorMaxForce = 10000;
    public double targetPosition;
    @Prop(min = 0) public double speed = 2;
    @Prop(min = 0) public double servoMaxForce = 10000;
    public boolean limitsEnabled;
    public double lowerLimit = -5;
    public double upperLimit = 5;

    @Prop(driven = true) public double currentPosition;
}
