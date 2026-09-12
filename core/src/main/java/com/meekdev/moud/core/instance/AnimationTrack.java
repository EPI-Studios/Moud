package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// one thing posing a body, among however many
//
// its children name joints and carry where those joints should be: a child called rightArm with a
// cframe on it is "put the right arm here". a track does not have to name them all, and what it
// does not name it does not touch
//
// two tracks at the same priority blend by weight. a higher priority wins outright over a lower
// one, which is the difference between a wave that survives walking and a sit that does not
public final class AnimationTrack extends Instance {

    public boolean playing;

    // how much of it to use, from none to all of it
    @Prop(min = 0, max = 1) public double weight = 1.0;

    // who wins. the engine's own walk is below every track a place makes
    public double priority;
}
