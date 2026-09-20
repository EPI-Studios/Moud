package com.meekdev.moud.core.part;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public class Part extends Spatial {

    public Vector3 size = Vector3.ONE;
    public PartShape shape = PartShape.BLOCK;
    public Color color = Color.WHITE;
    @Prop(min = 0, max = 1) public double transparency;
    public boolean anchored = true;
    public boolean collides = true;

    public boolean canQuery = true;

    public boolean canTouch = true;

    public boolean castShadow = true;

    public String collisionGroup = "default";

    public boolean locked;

    @Prop(min = 0.01) public double density = 1;
    @Prop(min = 0) public double friction = 0.5;
    @Prop(min = 0, max = 1) public double elasticity;
    public boolean massless;

    @Prop(driven = true) public Vector3 velocity = Vector3.ZERO;
    @Prop(driven = true) public Vector3 angularVelocity = Vector3.ZERO;

    @Prop(readOnly = true) public String networkOwner = "";

    private boolean ownershipSet;

    private int serverHold;

    private boolean ownerReporting;

    private int ownerSilence;

    public boolean simulatedRemotely() {
        return ownerReporting && !networkOwner.isEmpty();
    }

    public void ownerHeard() {
        ownerReporting = true;
        ownerSilence = 0;
    }

    public int ownerSilence() {
        return networkOwner.isEmpty() ? 0 : ++ownerSilence;
    }

    public void ownerChanged() {
        ownerReporting = false;
        ownerSilence = 0;
    }

    public void holdForServer(int ticks) {
        serverHold = Math.max(serverHold, ticks);
    }

    public boolean heldForServer() {
        if (serverHold <= 0) return false;
        serverHold--;
        return true;
    }

    public boolean ownershipSet() {
        return ownershipSet;
    }

    public void ownershipSet(boolean byScript) {
        ownershipSet = byScript;
    }

    public boolean simulatedBy(String player) {
        return !player.isEmpty() && !anchored && player.equals(networkOwner);
    }

    @Override
    public boolean sentBy(String player, int property) {
        return ownerReporting && simulatedBy(player) && (property == Motion.CFRAME || property == Motion.VELOCITY || property == Motion.ANGULAR_VELOCITY);
    }

    private static final class Motion {
        static final int CFRAME = Classes.SPATIAL.property("cframe").index();
        static final int VELOCITY = Classes.PART.property("velocity").index();
        static final int ANGULAR_VELOCITY = Classes.PART.property("angularVelocity").index();
    }

    public final Signal<Instance> touched = new Signal<>();
    public final Signal<Instance> touchEnded = new Signal<>();
}
