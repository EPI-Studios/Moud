package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;

public final class IKControl extends Instance {

    public IKControlType type = IKControlType.POSITION;

    public Instance chainRoot;

    public Instance endEffector;

    public Instance target;

    public CFrame targetCframe = CFrame.IDENTITY;

    public Instance pole;

    public CFrame offset = CFrame.IDENTITY;

    public Vector3 axis = new Vector3(0, -1, 0);

    @Prop(min = 0, max = 1) public double weight = 1;

    @Prop(min = 0, max = 1) public double rootWeight = 0.3;

    @Prop(min = 0) public double smoothTime = 0.05;

    public double priority;

    public boolean enabled = true;
}
