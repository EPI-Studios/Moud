package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// a glowing panel facing down the light's forward: a window, a screen, a ceiling tile
public final class AreaLight extends Light {

    public AreaShape shape = AreaShape.RECTANGLE;

    // in metres. a disc uses width as its diameter
    @Prop(min = 0) public double width = 1;
    @Prop(min = 0) public double height = 1;
}
