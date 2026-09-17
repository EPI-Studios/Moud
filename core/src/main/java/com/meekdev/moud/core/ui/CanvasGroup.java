package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public final class CanvasGroup extends GuiObject {

    @Prop(min = 0, max = 1) public double groupTransparency;

    public Color groupColor = Color.WHITE;
}
