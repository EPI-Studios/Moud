package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// a glowing line along the light's right axis: a neon tube, a fluorescent strip
public final class TubeLight extends Light {

    @Prop(min = 0) public double length = 1;
}
