package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

public final class Part extends Spatial {

    public Vec3 size = Vec3.ONE;
    public Color color = Color.WHITE;
    @Prop(min = 0, max = 1) public double transparency;
    public boolean anchored = true;
    public boolean collides = true;
}
