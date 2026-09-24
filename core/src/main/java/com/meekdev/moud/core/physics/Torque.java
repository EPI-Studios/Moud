package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.math.Vector3;

public final class Torque extends Constraint implements Mover {

    public Vector3 torque = Vector3.ZERO;
    public ActuatorRelativeTo relativeTo = ActuatorRelativeTo.ATTACHMENT0;
}
