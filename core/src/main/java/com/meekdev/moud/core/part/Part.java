package com.meekdev.moud.core.part;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public class Part extends Spatial {

    public Vector3 size = Vector3.ONE;
    public Color color = Color.WHITE;
    @Prop(min = 0, max = 1) public double transparency;
    public boolean anchored = true;
    public boolean collides = true;

    public boolean canQuery = true;

    public boolean canTouch = true;

    public String collisionGroup = "default";

    public boolean locked;

    public final Signal<Instance> touched = new Signal<>();
    public final Signal<Instance> touchEnded = new Signal<>();
}
