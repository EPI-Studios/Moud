package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Smoke extends Instance {

    public boolean enabled = true;

    @Prop(min = 0.1, max = 100) public double size = 1;

    @Prop(min = 0, max = 1) public double opacity = 0.5;

    @Prop(min = -25, max = 25) public double riseVelocity = 1;

    public Color color = Color.WHITE;

    @Prop(min = 0, max = 1) public double timeScale = 1;
}
