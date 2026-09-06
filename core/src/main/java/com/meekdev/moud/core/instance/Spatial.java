package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;

// a local frame, the world transform is the parent chain composed
public class Spatial extends Instance {

    public CFrame cframe = CFrame.IDENTITY;
    public boolean visible = true;
}
