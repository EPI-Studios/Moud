package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// the mix of every sound sent to one bus
public final class SoundBus extends Instance {

    public String bus = "sfx";

    @Prop(min = 0) public double volume = 1;
    public boolean muted;

    // how much of the high end gets through, 1 is all of it
    @Prop(min = 0, max = 1) public double lowPass = 1;
}
