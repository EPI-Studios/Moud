package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public final class ImageLabel extends GuiObject {

    @Prop(asset = true) public String image = "";
    public Color imageColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double imageTransparency;

    public ImageLabel() {
        backgroundTransparency = 1;
    }
}
