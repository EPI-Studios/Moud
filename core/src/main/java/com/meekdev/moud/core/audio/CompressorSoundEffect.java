package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;

public final class CompressorSoundEffect extends SoundEffect {

    @Prop(min = -80, max = 0) public double threshold = -20;

    @Prop(min = 1, max = 50) public double ratio = 5;

    @Prop(min = 0, max = 1) public double attack = 0.1;

    @Prop(min = 0, max = 5) public double release = 0.1;

    @Prop(min = -80, max = 10) public double makeupGain;
}
