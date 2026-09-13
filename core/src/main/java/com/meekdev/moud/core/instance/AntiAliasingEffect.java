package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// smooth edges, gathered over frames, then sharpened back
public final class AntiAliasingEffect extends PostEffect {

    @Prop(min = 0, max = 0.98) public double history = 0.9;
    @Prop(min = 0, max = 1) public double sharpness = 0.4;

    // how hard it refuses old frames that no longer match, which is what stops ghosting
    @Prop(min = 0.2, max = 3) public double clip = 1;
}
