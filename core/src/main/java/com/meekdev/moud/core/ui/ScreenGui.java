package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;

public final class ScreenGui extends Instance {

    public boolean enabled = true;

    public int displayOrder;

    @Prop(asset = true) public String font = "";

    public Vector3 designSize = Vector3.ZERO;
    public DesignScaling designScaling = DesignScaling.FIT;
}
