package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;

public class Spatial extends Instance {

    public CFrame cframe = CFrame.IDENTITY;
    public boolean visible = true;

    public Vector3 pivot = Vector3.ZERO;

    public boolean alwaysRelevant;

    public String owner = "";
}
