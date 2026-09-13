package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// a surface stuck flat on one face of a part, the size of that face
public final class SurfaceGui extends Instance {

    public boolean enabled = true;

    // the part it is stuck on. nothing means its parent
    public Instance adornee;

    public SurfaceFace face = SurfaceFace.FRONT;

    @Prop(min = 1) public double pixelsPerMetre = 50;

    @Prop(min = 0) public double maxDistance = 64;

    public boolean alwaysOnTop;
}
