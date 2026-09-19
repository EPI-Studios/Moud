package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;

public final class Bone extends Attachment {

    public CFrame transform = CFrame.IDENTITY;

    public Vector3 scale = Vector3.ONE;
}
