package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Sparkles extends Instance {

    public boolean enabled = true;

    public Color sparkleColor = new Color(144 / 255f, 25 / 255f, 1f);

    @Prop(min = 0, max = 1) public double timeScale = 1;
}
