package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Atmosphere extends Instance {

    @Prop(min = 0, max = 1) public double density = 0.3;

    @Prop(min = -1, max = 1) public double offset = 0.25;

    public Color color = new Color(199 / 255f, 199 / 255f, 199 / 255f);

    public Color decay = new Color(106 / 255f, 112 / 255f, 125 / 255f);

    @Prop(min = 0, max = 10) public double glare;

    @Prop(min = 0, max = 10) public double haze;
}
