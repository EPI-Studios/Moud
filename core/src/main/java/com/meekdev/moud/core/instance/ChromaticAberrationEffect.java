package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// red, green and blue pulled apart toward the edges, like a cheap lens
public final class ChromaticAberrationEffect extends ScreenEffect {

    // how far apart at the corners, as a fraction of the screen
    @Prop(min = 0) public double amount = 0.004;

    public ChromaticAberrationEffect() {
        order = 50;
    }
}
