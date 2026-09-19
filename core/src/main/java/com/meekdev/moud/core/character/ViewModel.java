package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Spatial;

public final class ViewModel extends Spatial {

    public boolean enabled;

    @Prop(asset = true) public String model = "";

    @Prop(min = 0, max = 170) public double fieldOfView;
}
