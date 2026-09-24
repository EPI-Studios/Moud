package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Vector3;

public final class AlignPosition extends Constraint implements Mover {

    public AlignmentMode mode = AlignmentMode.TWO_ATTACHMENT;
    public Vector3 position = Vector3.ZERO;
    public boolean rigidityEnabled;
    @Prop(min = 0) public double maxForce = 10000;
    @Prop(min = 0) public double maxVelocity = 1000;
    @Prop(min = 5, max = 200) public double responsiveness = 10;
    public boolean applyAtCenterOfMass;
    public boolean reactionForceEnabled;
}
