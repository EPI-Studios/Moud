package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;

public final class SpotLight extends LightSource {

    @Prop(min = 0, max = 90) public double innerAngle = 20;
    @Prop(min = 0, max = 90) public double outerAngle = 35;
}
