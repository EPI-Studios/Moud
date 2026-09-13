package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class FilmGrainEffect extends ScreenEffect {

    @Prop(min = 0) public double amount = 0.08;

    @Prop(min = 1) public double size = 1.5;

    public FilmGrainEffect() {
        order = 90;
    }
}
