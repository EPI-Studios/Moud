package com.meekdev.moud.core.render.post;

import com.meekdev.moud.core.clazz.Prop;

public final class AntiAliasingEffect extends PostEffect {

    @Prop(min = 0, max = 0.98) public double history = 0.9;
    @Prop(min = 0, max = 1) public double sharpness = 0.4;

    @Prop(min = 0.2, max = 3) public double clip = 1;
}
