package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;

public final class Texture extends Decal {

    @Prop(min = 0.001) public double studsPerTileU = 1;
    @Prop(min = 0.001) public double studsPerTileV = 1;

    public double offsetStudsU;
    public double offsetStudsV;
}
