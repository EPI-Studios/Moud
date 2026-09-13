package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// a cone down the light's forward. inside the inner angle it is full, and it fades out to the outer
public final class SpotLight extends Light {

    // in degrees, from the middle of the cone to its edge
    @Prop(min = 0, max = 90) public double innerAngle = 20;
    @Prop(min = 0, max = 90) public double outerAngle = 35;
}
