package com.meekdev.moud.core.audio;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class SoundBus extends Instance {

    public String bus = "sfx";

    @Prop(min = 0) public double volume = 1;
    public boolean muted;

    @Prop(min = 0, max = 1) public double lowPass = 1;
}
