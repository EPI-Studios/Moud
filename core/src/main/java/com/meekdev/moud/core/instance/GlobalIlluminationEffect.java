package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// light bouncing off lit surfaces onto the ones near them
public final class GlobalIlluminationEffect extends PostEffect {

    @Prop(min = 0.1) public double radius = 2;
    @Prop(min = 0) public double intensity = 1;
    @Prop(min = 0.25, max = 1) public double resolution = 0.75;

    // how much of the last frames carries over, which trades noise for lag
    @Prop(min = 0, max = 0.98) public double history = 0.9;
}
