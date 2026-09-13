package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class BloomEffect extends PostEffect {

    @Prop(min = 0) public double intensity = 1;

    @Prop(min = 0) public double threshold = 0.75;

    @Prop(min = 0, max = 1) public double knee = 0.5;

    @Prop(min = 2, max = 8) public int size = 6;

    @Prop(min = 0.05, max = 1) public double resolution = 0.5;

    public boolean occlude = true;
}
