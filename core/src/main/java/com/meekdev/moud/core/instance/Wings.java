package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class Wings extends Instance {

    public boolean worn;

    public double x;
    public double y;
    public double z;

    @Prop(asset = true) public String skin = "";
}
