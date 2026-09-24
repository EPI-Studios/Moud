package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Vector3;

public final class LinearVelocity extends Constraint implements Mover {

    public VelocityConstraintMode velocityConstraintMode = VelocityConstraintMode.VECTOR;
    public Vector3 vectorVelocity = Vector3.ZERO;
    public Vector3 lineDirection = Vector3.RIGHT;
    public double lineVelocity;
    public Vector3 planeVelocity = Vector3.ZERO;
    public Vector3 primaryTangentAxis = Vector3.RIGHT;
    public Vector3 secondaryTangentAxis = Vector3.UP;
    public ActuatorRelativeTo relativeTo = ActuatorRelativeTo.WORLD;
    public boolean forceLimitsEnabled = true;
    @Prop(min = 0) public double maxForce = 1000;
    public boolean reactionForceEnabled = true;
}
