package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;

public final class EqualizerSoundEffect extends SoundEffect {

    @Prop(min = -80, max = 10) public double lowGain;

    @Prop(min = -80, max = 10) public double midGain;

    @Prop(min = -80, max = 10) public double highGain;

    @Prop(min = 20, max = 20000) public double midLow = 500;

    @Prop(min = 20, max = 20000) public double midHigh = 3000;
}
