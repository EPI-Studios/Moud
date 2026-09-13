package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

public final class ProximityPrompt extends Instance {

    public boolean enabled = true;

    public String actionText = "Interact";
    public String objectText = "";

    public String keys = "e";

    @Prop(min = 0) public double holdDuration;

    @Prop(min = 0) public double maxActivationDistance = 10;

    public boolean requiresLineOfSight = true;

    public Vec3 offset = new Vec3(0, 1, 0);

    public Color backgroundColor = new Color(0.05f, 0.05f, 0.07f, 1);
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.25;
    public Color textColor = Color.WHITE;
    public Color keyColor = Color.WHITE;

    public final Signal<Instance> triggered = new Signal<>();
    public final Signal<Instance> holdBegan = new Signal<>();
    public final Signal<Instance> holdEnded = new Signal<>();

    public final Signal<Instance> shown = new Signal<>();
    public final Signal<Instance> hidden = new Signal<>();
}
