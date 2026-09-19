package com.meekdev.moud.core.character;

import com.meekdev.moud.core.instance.Instance;

public final class KeyframeSequence extends Instance {

    public boolean looped;

    public double priority;

    public String mask = "";

    public AnimationBlend blend = AnimationBlend.NORMAL;

    public AnimationSpace space = AnimationSpace.BODY;
}
