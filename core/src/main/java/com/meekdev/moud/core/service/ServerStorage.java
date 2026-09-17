package com.meekdev.moud.core.service;

import com.meekdev.moud.core.instance.Instance;

public final class ServerStorage extends Instance {

    @Override
    public boolean serverOnly() {
        return true;
    }

    @Override
    public boolean holdsTemplates() {
        return true;
    }
}
