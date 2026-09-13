package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// luau that runs on every client holding this, while it is in the tree and enabled. script is this
// instance, and destroying or disabling it stops everything it connected or scheduled
public final class LocalScript extends Instance {

    // a .luau file under client/ or shared/
    @Prop(asset = true) public String source = "";

    public String code = "";

    public boolean enabled = true;
}
