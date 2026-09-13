package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// how the lights and the sun cast shadows
public final class ShadowQuality extends PostEffect {

    // the size of each shadow map, a power of two
    @Prop(min = 256, max = 2048) public int resolution = 1024;
    @Prop(min = 256, max = 4096) public int sunResolution = 2048;
    @Prop(min = 1, max = 4) public int sunCascades = 4;
    @Prop(min = 16) public double sunDistance = 128;

    @Prop(min = 8) public double maxDistance = 64;
    @Prop(min = 0, max = 8) public double softness = 1.5;

    // softer the further a shadow is from what casts it
    public boolean contactHardening = true;
    @Prop(min = 0.1, max = 16) public double lightSize = 2.5;

    public boolean entities = true;
}
