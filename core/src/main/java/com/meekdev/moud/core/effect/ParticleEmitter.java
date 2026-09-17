package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.ui.SurfaceFace;

public final class ParticleEmitter extends Instance {

    public boolean enabled = true;

    @Prop(asset = true) public String texture = "";

    @Prop(min = 0) public double rate = 20;

    @Prop(min = 0) public double lifetimeMin = 5;
    @Prop(min = 0) public double lifetimeMax = 10;

    public double speedMin = 5;
    public double speedMax = 5;

    @Prop(min = 0, max = 180) public double spreadAngle;

    public SurfaceFace emissionDirection = SurfaceFace.TOP;

    public EmissionShape shapeStyle = EmissionShape.VOLUME;

    public Vector3 acceleration = Vector3.ZERO;

    @Prop(min = 0) public double drag;

    public double rotationMin;
    public double rotationMax;
    public double rotSpeedMin;
    public double rotSpeedMax;

    @Prop(min = 0) public double sizeStart = 1;
    @Prop(min = 0) public double sizeEnd = 1;

    @Prop(min = 0, max = 1) public double transparencyStart;
    @Prop(min = 0, max = 1) public double transparencyEnd;

    public Color colorStart = Color.WHITE;
    public Color colorEnd = Color.WHITE;

    @Prop(min = 0) public double brightness = 1;

    @Prop(min = 0, max = 1) public double lightEmission;
    @Prop(min = 0, max = 1) public double lightInfluence;

    public ParticleOrientation orientation = ParticleOrientation.FACING_CAMERA;

    public boolean lockedToPart;

    @Prop(min = 0, max = 1) public double timeScale = 1;

    @Prop(min = 0) public int emitted;

    private int queued;

    public void queue(int count) {
        queued += Math.max(0, count);
    }

    public int takeQueued() {
        int taken = queued;
        queued = 0;
        return taken;
    }
}
