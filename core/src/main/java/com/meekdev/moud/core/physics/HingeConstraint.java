package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;

public final class HingeConstraint extends Constraint {

    public ActuatorType actuatorType = ActuatorType.NONE;
    public double angularVelocity;
    @Prop(min = 0) public double motorMaxTorque = 10000;
    public double targetAngle;
    @Prop(min = 0) public double angularSpeed = 3;
    @Prop(min = 0) public double servoMaxTorque = 10000;
    public boolean limitsEnabled;
    @Prop(min = -180, max = 180) public double lowerAngle = -45;
    @Prop(min = -180, max = 180) public double upperAngle = 45;

    @Prop(driven = true, readOnly = true) public double currentAngle;
}
