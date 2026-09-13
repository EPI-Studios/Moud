package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public class ScreenEffect extends PostEffect {

    public double order = 50;

    @Prop(min = 0, max = 1) public double intensity = 1;
}
