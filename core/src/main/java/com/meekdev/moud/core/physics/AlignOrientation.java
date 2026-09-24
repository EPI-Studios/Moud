package com.meekdev.moud.core.physics;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.CFrame;

public final class AlignOrientation extends Constraint implements Mover {

    public AlignmentMode mode = AlignmentMode.TWO_ATTACHMENT;
    public CFrame cframe = CFrame.IDENTITY;
    public boolean primaryAxisOnly;
    public boolean rigidityEnabled;
    @Prop(min = 0) public double maxTorque = 10000;
    @Prop(min = 0) public double maxAngularVelocity = 1000;
    @Prop(min = 5, max = 200) public double responsiveness = 10;
    public boolean reactionTorqueEnabled;
}
