package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public final class OutlineEffect extends ScreenEffect {

    public Color color = Color.BLACK;

    @Prop(min = 0.5) public double thickness = 1;

    @Prop(min = 0.0001) public double threshold = 0.02;

    public OutlineEffect() {
        order = 5;
    }
}
