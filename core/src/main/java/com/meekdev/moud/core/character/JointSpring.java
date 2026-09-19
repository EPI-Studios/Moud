package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;

public final class JointSpring extends Instance {

    public Instance joint;

    @Prop(min = 0) public double stiffness = 60;

    @Prop(min = 0) public double damping = 8;

    public Vector3 gravity = new Vector3(0, -9.8, 0);

    public Vector3 axis = new Vector3(0, -1, 0);

    @Prop(min = 0.01) public double length = 0.5;

    @Prop(min = 0, max = 180) public double maxAngle = 60;

    @Prop(min = 0, max = 1) public double weight = 1;

    public boolean enabled = true;
}
