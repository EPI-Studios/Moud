package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class AnimationTrack extends Instance {

    public boolean playing;

    @Prop(min = 0, max = 1) public double weight = 1.0;

    public double priority;
}
