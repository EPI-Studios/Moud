package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;

public final class UIAspectRatioConstraint extends UIComponent {

    @Prop(min = 0.001) public double aspectRatio = 1;

    public AspectType aspectType = AspectType.FIT_WITHIN_MAX_SIZE;

    public DominantAxis dominantAxis = DominantAxis.WIDTH;
}
