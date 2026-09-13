package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// crisper edges
public final class SharpenEffect extends ScreenEffect {

    @Prop(min = 0) public double amount = 0.5;

    public SharpenEffect() {
        order = 80;
    }
}
