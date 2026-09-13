package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// an effect drawn over the finished picture by a shader, in order from lowest to highest. any number
// of them stack
public class ScreenEffect extends PostEffect {

    public double order = 50;

    // how much of it: zero is the picture as it was, one is the whole effect
    @Prop(min = 0, max = 1) public double intensity = 1;
}
