package com.meekdev.moud.core.part;

import com.meekdev.moud.core.clazz.Prop;

public final class VehicleSeat extends Seat {

    @Prop(min = 0) public double maxSpeed = 25;

    @Prop(min = 0) public double torque = 10;

    @Prop(min = 0) public double turnSpeed = 1;

    @Prop(readOnly = true, min = -1, max = 1) public double throttle;

    @Prop(readOnly = true, min = -1, max = 1) public double steer;
}
