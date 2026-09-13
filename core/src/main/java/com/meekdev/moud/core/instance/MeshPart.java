package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public class MeshPart extends Part {

    @Prop(asset = true) public String meshId = "";

    public String animation = "";

    @Prop(min = 0) public double animationSpeed = 1.0;

    public boolean animationLooped = true;
}
