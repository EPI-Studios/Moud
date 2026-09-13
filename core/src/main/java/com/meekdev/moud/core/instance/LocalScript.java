package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class LocalScript extends Instance {

    @Prop(asset = true) public String source = "";

    public String code = "";

    public boolean enabled = true;
}
