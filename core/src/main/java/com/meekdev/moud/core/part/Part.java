package com.meekdev.moud.core.part;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public class Part extends Spatial {

    public Vector3 size = Vector3.ONE;
    public PartShape shape = PartShape.BLOCK;
    public Color color = Color.WHITE;
    @Prop(min = 0, max = 1) public double transparency;
    public boolean anchored = true;
    public boolean collides = true;

    public boolean canQuery = true;

    public boolean canTouch = true;

    public String collisionGroup = "default";

    public boolean locked;

    @Prop(min = 0.01) public double density = 1;
    @Prop(min = 0) public double friction = 0.5;
    @Prop(min = 0, max = 1) public double elasticity;
    public boolean massless;

    @Prop(driven = true) public Vector3 velocity = Vector3.ZERO;
    @Prop(driven = true) public Vector3 angularVelocity = Vector3.ZERO;

    public final Signal<Instance> touched = new Signal<>();
    public final Signal<Instance> touchEnded = new Signal<>();
}
