package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class AreaLight extends Light {

    public AreaShape shape = AreaShape.RECTANGLE;

    @Prop(min = 0) public double width = 1;
    @Prop(min = 0) public double height = 1;
}
