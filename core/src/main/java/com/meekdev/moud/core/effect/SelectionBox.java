package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class SelectionBox extends Instance {

    public boolean visible = true;

    public Instance adornee;

    public Color color = new Color(13 / 255f, 105 / 255f, 172 / 255f);

    @Prop(min = 0, max = 1) public double transparency;

    public Color surfaceColor = new Color(13 / 255f, 105 / 255f, 172 / 255f);

    @Prop(min = 0, max = 1) public double surfaceTransparency = 1;

    @Prop(min = 0) public double lineThickness = 0.05;
}
