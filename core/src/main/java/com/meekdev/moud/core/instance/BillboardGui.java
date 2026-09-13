package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vec3;

// a surface that hangs in the world over a thing and always turns to face whoever is looking
public final class BillboardGui extends Instance {

    public boolean enabled = true;

    // what it hangs over. nothing means its parent
    public Instance adornee;

    // the scale is metres and the offset is pixels
    public UDim2 size = UDim2.fromScale(2, 1);

    // from the middle of what it hangs over, in metres
    public Vec3 offset = new Vec3(0, 2, 0);

    @Prop(min = 1) public double pixelsPerMetre = 50;

    // zero draws it at any distance
    @Prop(min = 0) public double maxDistance = 64;

    public boolean alwaysOnTop;

    // the font every text inside is drawn in unless it names its own
    @Prop(asset = true) public String font = "";
}
