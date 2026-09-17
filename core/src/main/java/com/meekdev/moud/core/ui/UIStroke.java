package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public final class UIStroke extends UIComponent {

    public boolean enabled = true;

    public Color color = Color.BLACK;

    @Prop(min = 0) public double thickness = 1;

    @Prop(min = 0, max = 1) public double transparency;

    public StrokeMode applyStrokeMode = StrokeMode.CONTEXTUAL;
}
