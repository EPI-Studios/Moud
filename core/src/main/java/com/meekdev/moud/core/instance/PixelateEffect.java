package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// chunky pixels
public final class PixelateEffect extends ScreenEffect {

    // how many screen pixels one block of colour covers
    @Prop(min = 1) public double pixelSize = 4;

    public PixelateEffect() {
        order = 70;
    }
}
