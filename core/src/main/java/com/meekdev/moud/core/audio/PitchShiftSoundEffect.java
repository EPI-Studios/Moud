package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;

public final class PitchShiftSoundEffect extends SoundEffect {

    @Prop(min = 0.5, max = 2) public double octave = 1;
}
