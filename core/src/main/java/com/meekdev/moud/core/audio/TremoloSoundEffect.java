package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;

public final class TremoloSoundEffect extends SoundEffect {

    @Prop(min = 0, max = 1) public double depth = 1;

    @Prop(min = 0.01, max = 0.99) public double duty = 0.5;

    @Prop(min = 0.01, max = 20) public double frequency = 5;
}
