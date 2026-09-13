package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class ScreenGui extends Instance {

    public boolean enabled = true;

    public int displayOrder;

    @Prop(asset = true) public String font = "";
}
