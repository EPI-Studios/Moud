package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Beam extends Instance {

    public boolean enabled = true;

    public Instance attachment0;
    public Instance attachment1;

    @Prop(asset = true) public String texture = "";

    @Prop(min = 0.001) public double textureLength = 1;

    public double textureSpeed = 1;

    public TextureMode textureMode = TextureMode.STRETCH;

    public Color colorStart = Color.WHITE;
    public Color colorEnd = Color.WHITE;

    @Prop(min = 0, max = 1) public double transparencyStart = 0.5;
    @Prop(min = 0, max = 1) public double transparencyEnd = 0.5;

    @Prop(min = 0) public double width0 = 1;
    @Prop(min = 0) public double width1 = 1;

    public double curveSize0;
    public double curveSize1;

    @Prop(min = 1, max = 1000) public int segments = 10;

    public boolean faceCamera;

    @Prop(min = 0) public double brightness = 1;

    @Prop(min = 0, max = 1) public double lightEmission;
    @Prop(min = 0, max = 1) public double lightInfluence;
}
