package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;

public final class FlangeSoundEffect extends SoundEffect {

    @Prop(min = 0, max = 1) public double depth = 0.25;

    @Prop(min = 0, max = 1) public double mix = 0.5;

    @Prop(min = 0, max = 20) public double rate = 5;
}
