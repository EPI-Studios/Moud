package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;

public final class Transforms {

    private Transforms() {}

    public static CFrame world(Instance i) {
        CFrame local = local(i);
        Instance parent = i.parent();
        if (parent == null) return local;
        return world(parent).mul(local);
    }

    public static CFrame local(Instance i) {
        if (!(i instanceof Spatial s)) return CFrame.IDENTITY;
        if (s.pivot.equals(Vec3.ZERO)) return s.cframe;
        return s.cframe.mul(CFrame.at(s.pivot.neg()));
    }

    public static CFrame localFor(Instance i, CFrame worldTarget) {
        Instance parent = i.parent();
        if (parent == null) return worldTarget;
        return world(parent).inverse().mul(worldTarget);
    }
}
