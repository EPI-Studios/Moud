package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;

public final class ReverbSoundEffect extends SoundEffect {

    @Prop(min = 0.1, max = 20) public double decayTime = 1.5;

    @Prop(min = 0, max = 1) public double density = 1;

    @Prop(min = 0, max = 1) public double diffusion = 1;

    @Prop(min = -80, max = 10) public double dryLevel;

    @Prop(min = -80, max = 10) public double wetLevel = -6;
}
