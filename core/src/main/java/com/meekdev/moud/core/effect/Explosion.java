package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;

public final class Explosion extends Instance {

    public final Signal<Object[]> hit = new Signal<>();

    public Vector3 position = Vector3.ZERO;

    @Prop(min = 0, max = 512) public double blastRadius = 4;

    @Prop(min = 0) public double blastPressure = 500000;

    @Prop(min = 0, max = 1) public double destroyJointRadiusPercent = 1;

    public ExplosionType explosionType = ExplosionType.CRATERS;

    public boolean visible = true;
}
