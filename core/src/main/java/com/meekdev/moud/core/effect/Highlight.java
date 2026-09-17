package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Highlight extends Instance {

    public boolean enabled = true;

    public Instance adornee;

    public Color fillColor = new Color(1f, 0f, 0f);

    @Prop(min = 0, max = 1) public double fillTransparency = 0.5;

    public Color outlineColor = Color.WHITE;

    @Prop(min = 0, max = 1) public double outlineTransparency;

    public DepthMode depthMode = DepthMode.ALWAYS_ON_TOP;
}
