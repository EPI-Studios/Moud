package com.meekdev.moud.core.script;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.service.ReplicatedStorage;
import com.meekdev.moud.core.service.StarterCharacterScripts;
import com.meekdev.moud.core.service.StarterGui;
import com.meekdev.moud.core.service.StarterPlayerScripts;

public final class ModuleScript extends Instance {

    @Prop(asset = true) public String source = "";

    public String code = "";

    public boolean forClients() {
        for (Instance up = parent(); up != null; up = up.parent()) {
            if (up.serverOnly()) return false;
            if (up instanceof LocalScript || up instanceof ReplicatedStorage || up instanceof StarterGui
                    || up instanceof StarterPlayerScripts || up instanceof StarterCharacterScripts) {
                return true;
            }
        }
        return source.startsWith(Res.SCHEME + "client/") || source.startsWith(Res.SCHEME + "shared/");
    }

    @Override
    protected long externalPropertyMask() {
        return forClients() ? 0 : 1L << def().property("code").index();
    }
}
