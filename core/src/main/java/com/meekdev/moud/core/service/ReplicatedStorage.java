package com.meekdev.moud.core.service;

import com.meekdev.moud.core.instance.Instance;

public final class ReplicatedStorage extends Instance {

    @Override
    public boolean holdsTemplates() {
        return true;
    }
}
