package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.math.Vector3;

public final class VectorForce extends Constraint implements Mover {

    public Vector3 force = Vector3.ZERO;
    public ActuatorRelativeTo relativeTo = ActuatorRelativeTo.ATTACHMENT0;
    public boolean applyAtCenterOfMass;
}
