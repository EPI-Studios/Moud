package com.meekdev.moud.core.render.post;

import com.meekdev.moud.core.clazz.Prop;

public final class ColorGradeEffect extends PostEffect {

    @Prop(min = 0) public double exposure = 1;
    @Prop(min = 0) public double contrast = 1;
    @Prop(min = 0) public double saturation = 1;
    public double brightness;

    @Prop(min = -1, max = 1) public double temperature;
    @Prop(min = -1, max = 1) public double tint;

    @Prop(min = 0.01) public double gamma = 1;

    @Prop(asset = true) public String lut = "";
    @Prop(min = 2) public int lutSize = 16;
    @Prop(min = 0, max = 1) public double lutIntensity = 1;
}
