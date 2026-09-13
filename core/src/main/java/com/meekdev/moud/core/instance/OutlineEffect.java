package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

// lines where the depth jumps, which is the edge of every silhouette, for a drawn look
public final class OutlineEffect extends ScreenEffect {

    public Color color = Color.BLACK;

    // in pixels
    @Prop(min = 0.5) public double thickness = 1;

    // how big a jump in depth counts as an edge, as a fraction of the distance
    @Prop(min = 0.0001) public double threshold = 0.02;

    public OutlineEffect() {
        order = 5;
    }
}
