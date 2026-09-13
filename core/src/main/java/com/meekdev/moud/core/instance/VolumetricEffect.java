package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// light scattering in the air: shafts through windows, glow around lamps
public final class VolumetricEffect extends PostEffect {

    @Prop(min = 0) public double strength = 1;
    @Prop(min = 0) public double density = 0.4;

    // how much the light scatters forward rather than every way
    @Prop(min = 0, max = 0.95) public double anisotropy = 0.6;

    @Prop(min = 1, max = 64) public int steps = 16;
    @Prop(min = 0.25, max = 1) public double resolution = 0.5;
    public boolean shadows = true;
}
