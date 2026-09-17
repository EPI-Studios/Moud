package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.ui.SurfaceFace;

public class Decal extends Instance {

    @Prop(asset = true) public String texture = "";

    public SurfaceFace face = SurfaceFace.FRONT;

    public Color color = Color.WHITE;

    @Prop(min = 0, max = 1) public double transparency;

    public int zIndex = 1;
}
