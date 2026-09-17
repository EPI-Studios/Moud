package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Fire extends Instance {

    public boolean enabled = true;

    @Prop(min = 0.2, max = 30) public double size = 1.5;

    @Prop(min = 0, max = 25) public double heat = 9;

    public Color color = new Color(236 / 255f, 139 / 255f, 70 / 255f);

    public Color secondaryColor = new Color(139 / 255f, 80 / 255f, 55 / 255f);

    @Prop(min = 0, max = 1) public double timeScale = 1;
}
