package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.event.Signal;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

public class Part extends Spatial {

    public Vec3 size = Vec3.ONE;
    public Color color = Color.WHITE;
    @Prop(min = 0, max = 1) public double transparency;
    public boolean anchored = true;
    public boolean collides = true;

    // whether casts and overlaps can find it
    public boolean canQuery = true;

    // whether it takes part in touched and touchEnded, on either end
    public boolean canTouch = true;

    // another part began or stopped touching this one. worked out each tick on each side, for parts
    // something is listening on
    public final Signal<Instance> touched = new Signal<>();
    public final Signal<Instance> touchEnded = new Signal<>();
}
