package com.meekdev.moud.core.service;

import com.meekdev.moud.core.instance.Instance;

public final class ServerScriptService extends Instance {

    @Override
    public boolean serverOnly() {
        return true;
    }
}
