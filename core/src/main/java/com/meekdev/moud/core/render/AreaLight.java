package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;

public final class AreaLight extends LightSource {

    public AreaShape shape = AreaShape.RECTANGLE;

    @Prop(min = 0) public double width = 1;
    @Prop(min = 0) public double height = 1;
}
