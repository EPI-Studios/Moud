package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class AnimationTrack extends Instance {

    public boolean playing;

    @Prop(min = 0, max = 1) public double weight = 1.0;

    public double priority;
}
