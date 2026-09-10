package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;

// a local frame, the world transform is the parent chain composed
public class Spatial extends Instance {

    public CFrame cframe = CFrame.IDENTITY;
    public boolean visible = true;

    // the point the frame turns about, from the middle of the thing outward
    //
    // a limb rotates at the shoulder and a door at its hinge, neither at its own centre. without
    // this a rig needs an empty frame per joint, which is a whole concept to carry for an offset
    public Vec3 pivot = Vec3.ZERO;
}
