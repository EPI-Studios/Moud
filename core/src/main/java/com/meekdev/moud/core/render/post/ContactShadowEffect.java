package com.meekdev.moud.core.render.post;

import com.meekdev.moud.core.clazz.Prop;

public final class ContactShadowEffect extends PostEffect {

    @Prop(min = 0) public double distance = 0.5;
    @Prop(min = 0) public double thickness = 0.5;
    @Prop(min = 1, max = 64) public int steps = 12;
}
