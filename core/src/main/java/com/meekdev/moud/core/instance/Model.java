package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class Model extends Spatial {

    public Instance primaryPart;

    @Prop(min = 0.001) public double scale = 1;
}
