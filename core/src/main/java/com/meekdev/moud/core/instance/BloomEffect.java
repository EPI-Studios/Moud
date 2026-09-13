package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// light bleeding out of bright things
public final class BloomEffect extends PostEffect {

    @Prop(min = 0) public double intensity = 1;

    // how bright a pixel has to be to glow. zero leaves the whole scene alone and only glowing parts bloom
    @Prop(min = 0) public double threshold = 0.75;

    // how softly things just under the threshold start to glow
    @Prop(min = 0, max = 1) public double knee = 0.5;

    // how far the glow spreads, in halvings of the picture
    @Prop(min = 2, max = 8) public int size = 6;

    @Prop(min = 0.05, max = 1) public double resolution = 0.5;

    // whether a wall in front of something bright hides its glow
    public boolean occlude = true;
}
