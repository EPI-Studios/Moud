package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;

public class Spatial extends Instance {

    public CFrame cframe = CFrame.IDENTITY;
    public boolean visible = true;

    public Vec3 pivot = Vec3.ZERO;

    public boolean alwaysRelevant;

    public String owner = "";
}
