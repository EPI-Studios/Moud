package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

// a light in the tree. it sits where its frame puts it, so a lamp parented to a part moves with the
// part, and it faces down its frame's forward
public class Light extends Spatial {

    public boolean enabled = true;

    public Color color = Color.WHITE;

    // a colour temperature in kelvin, which replaces color when it is above zero
    @Prop(min = 0) public double temperature;

    @Prop(min = 0) public double brightness = 1.0;

    // how far it reaches, in metres
    @Prop(min = 0) public double range = 12.0;

    public LightFalloff falloff = LightFalloff.SMOOTH;

    // the power, for exponent falloff
    @Prop(min = 0) public double falloffExponent = 2.0;

    public boolean shadows;
    @Prop(min = 0, max = 1) public double shadowStrength = 1.0;

    // shafts of light through the air around it. zero is none
    @Prop(min = 0) public double godrays;

    // a glsl file that changes how this light lands, like res://lights/flicker.glsl. the file is the
    // body of a function that may change `color`, `atten` and `p`, and can read `lightPos`, `lightDir`,
    // `stage` and `LightTime`
    @Prop(asset = true) public String shader = "";
}
