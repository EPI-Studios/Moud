package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Vec3;

// the client's camera, and only the client's: 4.3 gives the server no camera to own, so this is a
// local instance and never replicates
//
// cframe is where the camera is. in the follow modes the adapter writes it every frame and a
// place reads it; in scriptable the place writes it and the adapter reads it. same property,
// because a camera has one pose and two of them would drift
public final class Camera extends Spatial {

    public CameraMode mode = CameraMode.FIRST_PERSON;

    // degrees of view, or 0 for the player's own: their fov setting, widening as they sprint. a number
    // holds it there in every mode
    @Prop(min = 0, max = 179) public double fov = 0;

    // how far behind the subject the third person camera sits
    @Prop(min = 0) public double distance = 4.0;

    // from the subject's feet to its eye, which is not the middle of it
    public Vec3 offset = new Vec3(0, 1.62, 0);

    // what the follow modes follow. nil means the player, which is what a place that never
    // mentions it wants
    public Instance subject;
}
