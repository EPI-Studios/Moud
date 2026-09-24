package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Vector3;

public final class AngularVelocity extends Constraint implements Mover {

    public Vector3 angularVelocity = Vector3.ZERO;
    public ActuatorRelativeTo relativeTo = ActuatorRelativeTo.WORLD;
    @Prop(min = 0) public double maxTorque = 1000;
    public boolean reactionTorqueEnabled;
}
