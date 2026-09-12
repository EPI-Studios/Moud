package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;

// what makes a body a living thing rather than a pile of boxes
//
// the body is shape: six limbs, what it wears, where its joints are. this is everything else --
// how much life is in it, what it is doing, and how it moves when something tells it to. they are
// separate objects because they are separate questions, and because a class that answers both
// runs out of the sixty four properties a dirty mask holds
//
// a place reaches it the way it reaches anything: character.humanoid
public final class Humanoid extends Instance {

    // no life left. it fires once, on the tick it runs out, and not again until something gives
    // it life back
    public final Signal<Instance> died = new Signal<>();

    // life went up or down, however it went
    public final Signal<Instance> healthChanged = new Signal<>();

    // it started doing something else. one handler covers landing, jumping, sitting and dying,
    // because they are one question
    public final Signal<Instance> stateChanged = new Signal<>();

    // it got where it was told to go
    public final Signal<Instance> arrived = new Signal<>();

    // at zero the body is dead. the engine does not decide what that means -- it sets the state
    // and stops driving the body, and a place decides whether that is a respawn, a ragdoll or a
    // scoreboard
    @Prop(min = 0) public double health = 20.0;

    @Prop(min = 0) public double maxHealth = 20.0;

    // what the body is doing, which is one word rather than six booleans nobody can keep
    // consistent. the engine writes it and a place may force it
    public HumanoidState state = HumanoidState.STANDING;

    // where it has been told to walk, and whether it has been told at all
    //
    // a body with somewhere to go walks there on its own: this is how a place makes anything that
    // is not a player move, and it is the one thing our characters could not do at all
    public com.meekdev.moud.core.math.Vec3 walkTo = com.meekdev.moud.core.math.Vec3.ZERO;

    public boolean walking;

    // how close counts as arrived
    @Prop(min = 0) public double walkRadius = 0.5;

    @Prop(min = 0) public double walkSpeed = 4.317;
    @Prop(min = 0) public double sprintMultiplier = 1.3;
    @Prop(min = 0) public double sneakMultiplier = 0.3;

    // the speed you leave the ground at, not a height
    @Prop(min = 0) public double jumpPower = 8.4;

    @Prop(min = 0) public double gravityScale = 1.0;
    @Prop(min = 0) public double groundAcceleration = 36.0;
    @Prop(min = 0) public double groundDeceleration = 56.0;
    @Prop(min = 0) public double airSpeed = 4.317;
    @Prop(min = 0) public double airAcceleration = 8.0;

    // the fraction of speed a second of air leaves you with, so 1 is frictionless
    @Prop(min = 0, max = 1) public double airDrag = 0.667;
    @Prop(min = 0, max = 1) public double fallDrag = 0.667;

    @Prop(min = 0) public double stepHeight = 0.6;
    @Prop(min = 0, max = 90) public double slopeLimit = 45.0;
    @Prop(min = 0) public double slideAcceleration = 32.0;

    @Prop(min = 0) public double coyoteTime = 0.15;
    @Prop(min = 0) public double jumpBuffer = 0.15;

    public boolean followSlopes = true;
}
