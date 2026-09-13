package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// the whole picture out of focus, for a pause menu or a daze
public final class BlurEffect extends ScreenEffect {

    // in pixels
    @Prop(min = 0) public double size = 8;

    public BlurEffect() {
        order = 40;
    }
}
