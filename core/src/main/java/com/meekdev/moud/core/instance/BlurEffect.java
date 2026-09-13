package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class BlurEffect extends ScreenEffect {

    @Prop(min = 0) public double size = 8;

    public BlurEffect() {
        order = 40;
    }
}
