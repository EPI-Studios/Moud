package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class SurfaceGui extends Instance {

    public boolean enabled = true;

    public Instance adornee;

    public SurfaceFace face = SurfaceFace.FRONT;

    @Prop(min = 1) public double pixelsPerMetre = 50;

    @Prop(min = 0) public double maxDistance = 64;

    public boolean alwaysOnTop;

    @Prop(asset = true) public String font = "";
}
