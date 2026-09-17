package com.meekdev.moud.core.character;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import java.util.EnumSet;
import java.util.Set;

public final class Humanoid extends Instance {

    public final Signal<Instance> died = new Signal<>();

    public final Signal<Instance> healthChanged = new Signal<>();

    public final Signal<Instance> stateChanged = new Signal<>();

    public final Signal<Instance> arrived = new Signal<>();

    public final Signal<Double> running = new Signal<>();

    public final Signal<Boolean> jumping = new Signal<>();

    public final Signal<Boolean> freeFalling = new Signal<>();

    public final Signal<Double> swimming = new Signal<>();

    public final Signal<Double> climbing = new Signal<>();

    public final Signal<Boolean> seated = new Signal<>();

    public final Signal<Boolean> platformStanding = new Signal<>();

    public final Signal<Double> damaged = new Signal<>();

    public Vector3 moveDirection = Vector3.ZERO;

    public String floorMaterial = "";

    public boolean sit;

    public boolean platformStand;

    public boolean autoRotate = true;

    @Prop(min = 0) public double healthRegen;

    private final EnumSet<HumanoidState> disabled = EnumSet.noneOf(HumanoidState.class);

    public boolean stateEnabled(HumanoidState state) {
        return !disabled.contains(state);
    }

    public void stateEnabled(HumanoidState state, boolean on) {
        if (on) disabled.remove(state); else disabled.add(state);
    }

    public Set<HumanoidState> disabledStates() {
        return Set.copyOf(disabled);
    }

    @Prop(min = 0) public double health = 20.0;

    @Prop(min = 0) public double maxHealth = 20.0;

    public HumanoidState state = HumanoidState.STANDING;

    public Vector3 walkTo = Vector3.ZERO;

    public boolean walking;

    public boolean jump;

    @Prop(min = 0) public double walkRadius = 0.5;

    @Prop(min = 0) public double walkSpeed = 4.317;
    @Prop(min = 0) public double sprintMultiplier = 1.3;
    @Prop(min = 0) public double sneakMultiplier = 0.3;

    @Prop(min = 0) public double jumpPower = 8.4;

    @Prop(min = 0) public double gravityScale = 1.0;

    @Prop(min = 0) public double groundAcceleration = 22.7;
    @Prop(min = 0) public double groundDeceleration = 22.7;

    @Prop(min = 0) public double airSpeed = 4.444;
    @Prop(min = 0) public double airAcceleration = 3.64;

    @Prop(min = 0, max = 1) public double airDrag = 0.1516;
    @Prop(min = 0, max = 1) public double fallDrag = 0.6676;

    @Prop(min = 0) public double stepHeight = 0.6;
    @Prop(min = 0, max = 90) public double slopeLimit = 45.0;
    @Prop(min = 0) public double slideAcceleration = 32.0;

    @Prop(min = 0) public double coyoteTime = 0.15;
    @Prop(min = 0) public double jumpBuffer = 0.15;

    public boolean followSlopes = true;
}
