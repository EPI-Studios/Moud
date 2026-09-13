package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Vec3;

public final class Camera extends Spatial {

    public CameraMode mode = CameraMode.FIRST_PERSON;

    @Prop(min = 0, max = 179) public double fov = 0;

    @Prop(min = 0) public double distance = 4.0;

    public Vec3 offset = new Vec3(0, 1.62, 0);

    public Instance subject;
}
