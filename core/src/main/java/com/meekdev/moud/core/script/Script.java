package com.meekdev.moud.core.script;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class Script extends Instance {

    @Prop(asset = true) public String source = "";

    @Prop(replicated = false) public String code = "";

    public boolean enabled = true;
}
