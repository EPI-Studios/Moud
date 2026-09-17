package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public final class Lighting extends Instance {

    @Prop(min = 0, max = 24) public double clockTime = 14;

    @Prop(min = 0, max = 60) public double timeScale = 1;

    @Prop(min = -90, max = 90) public double geographicLatitude = 41.733;

    @Prop(min = 0, max = 10) public double brightness = 2;

    public Color ambient = Color.BLACK;

    public Color outdoorAmbient = new Color(0.5f, 0.5f, 0.5f);

    @Prop(min = -5, max = 5) public double exposureCompensation;

    @Prop(min = 0, max = 20) public double shadowSoftness = 0.2;

    public boolean globalShadows = true;

    @Prop(min = 0, max = 10) public double environmentDiffuseScale = 1;

    @Prop(min = 0, max = 10) public double environmentSpecularScale = 1;

    @Prop(min = 0, max = 1) public double rain;

    @Prop(min = 0, max = 1) public double thunder;
}
