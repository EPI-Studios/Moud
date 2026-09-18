package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;

public class ImageLabel extends GuiObject {

    @Prop(asset = true) public String image = "";
    public Color imageColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double imageTransparency;
    public ScaleType scaleType = ScaleType.STRETCH;
    public ResampleMode resampleMode = ResampleMode.DEFAULT;
    public UDim2 tileSize = UDim2.fromScale(1, 1);
    public Vector3 sliceMin = Vector3.ZERO;
    public Vector3 sliceMax = Vector3.ZERO;
    @Prop(min = 0.01) public double sliceScale = 1;
    public Vector3 imageRectOffset = Vector3.ZERO;
    public Vector3 imageRectSize = Vector3.ZERO;

    public ImageLabel() {
        backgroundTransparency = 1;
    }
}
