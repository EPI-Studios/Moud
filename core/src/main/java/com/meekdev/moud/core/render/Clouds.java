package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Clouds extends Instance {

    public boolean enabled = true;

    @Prop(min = 0, max = 1) public double cover = 0.5;

    @Prop(min = 0, max = 1) public double density = 0.7;

    public Color color = Color.WHITE;
}
