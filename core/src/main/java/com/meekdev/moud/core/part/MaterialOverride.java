package com.meekdev.moud.core.part;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public class MaterialOverride extends Instance {

    public String material = "";

    public Color color = Color.WHITE;

    @Prop(asset = true) public String texture = "";

    @Prop(min = -1, max = 1) public double roughness = -1;

    @Prop(min = -1, max = 1) public double metallic = -1;

    public Color emission = Color.BLACK;

    @Prop(min = 0) public double emissionStrength = 1;

    @Prop(min = 0, max = 1) public double transparency;

    public boolean doubleSided;

    public TextureFilter filter = TextureFilter.DEFAULT;

    public double offsetU;

    public double offsetV;

    public double repeatU = 1;

    public double repeatV = 1;
}
