package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Trail extends Instance {

    public boolean enabled = true;

    public Instance attachment0;
    public Instance attachment1;

    @Prop(asset = true) public String texture = "";

    @Prop(min = 0.001) public double textureLength = 1;

    public TextureMode textureMode = TextureMode.STRETCH;

    @Prop(min = 0, max = 20) public double lifetime = 2;

    @Prop(min = 0) public double minLength = 0.1;

    @Prop(min = 0) public double maxLength;

    public Color colorStart = Color.WHITE;
    public Color colorEnd = Color.WHITE;

    @Prop(min = 0, max = 1) public double transparencyStart;
    @Prop(min = 0, max = 1) public double transparencyEnd = 1;

    @Prop(min = 0) public double widthScaleStart = 1;
    @Prop(min = 0) public double widthScaleEnd = 1;

    public boolean faceCamera;

    @Prop(min = 0) public double brightness = 1;

    @Prop(min = 0, max = 1) public double lightEmission;
    @Prop(min = 0, max = 1) public double lightInfluence;

    @Prop(min = 0) public int clears;

    private boolean cleared;

    public void requestClear() {
        cleared = true;
    }

    public boolean takeClearRequest() {
        boolean was = cleared;
        cleared = false;
        return was;
    }
}
