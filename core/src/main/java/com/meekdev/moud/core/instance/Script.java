package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// luau that runs on the server while this is in the tree and enabled, with script set to this instance.
// destroying or disabling it stops everything it connected or scheduled
public final class Script extends Instance {

    // a .luau file under server/ or shared/
    @Prop(asset = true) public String source = "";

    // the code itself, used instead of source when set. never sent to a client
    @Prop(replicated = false) public String code = "";

    public boolean enabled = true;
}
