package com.meekdev.moud.core.render.post;

import com.meekdev.moud.core.clazz.Prop;

public final class ReflectionEffect extends PostEffect {

    @Prop(min = 0) public double intensity = 1;

    @Prop(min = 0, max = 1) public double reflectivity = 0.04;

    @Prop(min = 0.1) public double maxDistance = 32;
    @Prop(min = 1, max = 256) public int steps = 32;
    @Prop(min = 0.01) public double thickness = 1;
    @Prop(min = 0, max = 0.5) public double edgeFade = 0.1;
    @Prop(min = 0.1, max = 1) public double resolution = 1;
    public boolean temporal = true;
}
