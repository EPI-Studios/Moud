package com.meekdev.moud.core.render.post;

import com.meekdev.moud.core.clazz.Prop;

public final class ChromaticAberrationEffect extends ScreenEffect {

    @Prop(min = 0) public double amount = 0.004;

    public ChromaticAberrationEffect() {
        order = 50;
    }
}
