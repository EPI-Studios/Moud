package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;

public final class Transforms {

    private Transforms() {}

    public static CFrame world(Instance i) {
        CFrame local = i instanceof Spatial s ? s.cframe : CFrame.IDENTITY;
        Instance parent = i.parent();
        if (parent == null) return local;
        return world(parent).mul(local);
    }

    public static CFrame localFor(Instance i, CFrame worldTarget) {
        Instance parent = i.parent();
        if (parent == null) return worldTarget;
        return world(parent).inverse().mul(worldTarget);
    }
}
