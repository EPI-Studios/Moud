package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;

public final class BillboardGui extends Instance {

    public boolean enabled = true;

    public Instance adornee;

    public UDim2 size = UDim2.fromScale(2, 1);

    public Vector3 offset = new Vector3(0, 2, 0);

    @Prop(min = 1) public double pixelsPerMetre = 50;

    @Prop(min = 0) public double maxDistance = 64;

    public boolean alwaysOnTop;

    @Prop(asset = true) public String font = "";
}
