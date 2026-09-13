package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// the soft dark in corners and under things
public final class AmbientOcclusionEffect extends PostEffect {

    @Prop(min = 0.05) public double radius = 0.8;
    @Prop(min = 0) public double intensity = 1.2;
    @Prop(min = 0) public double bias = 0.025;
    @Prop(min = 0.1) public double power = 1.5;
    @Prop(min = 0.25, max = 1) public double resolution = 0.5;

    // smoothed over several frames, which removes noise and adds a little lag
    public boolean temporal = true;
}
