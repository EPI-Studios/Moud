package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;

public final class Camera extends Spatial {

    public CameraMode mode = CameraMode.FIRST_PERSON;

    @Prop(min = 0, max = 179) public double fov = 0;

    @Prop(min = 0) public double distance = 4.0;

    public Vector3 offset = new Vector3(0, 1.62, 0);

    public Instance subject;
}
