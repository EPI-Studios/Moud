package com.meekdev.moud.core.script;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;

public final class ModuleScript extends Instance {

    @Prop(asset = true) public String source = "";

    public String code = "";

    public boolean forClients() {
        if (source.startsWith(Res.SCHEME + "client/") || source.startsWith(Res.SCHEME + "shared/")) return true;
        for (Instance up = parent(); up != null; up = up.parent()) {
            if (up instanceof LocalScript) return true;
        }
        return false;
    }

    @Override
    protected long externalPropertyMask() {
        return forClients() ? 0 : 1L << def().property("code").index();
    }
}
