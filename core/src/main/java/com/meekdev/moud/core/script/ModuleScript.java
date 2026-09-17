package com.meekdev.moud.core.script;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class ModuleScript extends Instance {

    @Prop(asset = true) public String source = "";

    public String code = "";
}
