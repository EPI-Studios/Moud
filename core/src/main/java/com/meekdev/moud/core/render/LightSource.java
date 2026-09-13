package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Color;

public class LightSource extends Spatial {

    public boolean enabled = true;

    public Color color = Color.WHITE;

    @Prop(min = 0) public double temperature;

    @Prop(min = 0) public double brightness = 1.0;

    @Prop(min = 0) public double range = 12.0;

    public LightFalloff falloff = LightFalloff.SMOOTH;

    @Prop(min = 0) public double falloffExponent = 2.0;

    public boolean shadows;
    @Prop(min = 0, max = 1) public double shadowStrength = 1.0;

    @Prop(min = 0) public double godrays;

    @Prop(asset = true) public String shader = "";
}
