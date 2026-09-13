package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class ScreenGui extends Instance {

    public boolean enabled = true;

    public int displayOrder;

    @Prop(asset = true) public String font = "";
}
