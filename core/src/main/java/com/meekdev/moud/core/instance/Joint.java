package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;

public class Joint extends Instance {

    private static final PropertyDef CFRAME = Classes.SPATIAL.property("cframe");

    public Instance part0;
    public Instance part1;

    public CFrame c0 = CFrame.IDENTITY;
    public CFrame c1 = CFrame.IDENTITY;

    public CFrame transform = CFrame.IDENTITY;

    public Vec3 scale = Vec3.ONE;

    @Override
    protected void compose() {
        if (!(part1 instanceof Spatial held) || !held.isAlive()) return;
        if (part0 == null || !part0.isAlive()) return;
        Instances.setObj(held, CFRAME, base().mul(c0).mul(transform).mul(inverse(c1)));
    }

    private CFrame base() {
        Instance under = part1.parent();
        if (under == part0) return CFrame.IDENTITY;
        if (under == part0.parent()) return Transforms.local(part0);
        return Transforms.world(under).inverse().mul(Transforms.world(part0));
    }

    private static CFrame inverse(CFrame frame) {
        return frame.equals(CFrame.IDENTITY) ? CFrame.IDENTITY : frame.inverse();
    }
}
