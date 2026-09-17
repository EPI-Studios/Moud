package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;

public final class EchoSoundEffect extends SoundEffect {

    @Prop(min = 0.01, max = 5) public double delay = 1;

    @Prop(min = 0, max = 1) public double feedback = 0.5;

    @Prop(min = -80, max = 10) public double dryLevel;

    @Prop(min = -80, max = 10) public double wetLevel;
}
