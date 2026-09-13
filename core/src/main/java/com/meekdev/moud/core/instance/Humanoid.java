package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.Vec3;
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
    public Vec3 walkTo = Vec3.ZERO;

    public boolean walking;

    // set to jump once; the engine clears it. only for bodies a place moves: a player jumps themselves
    public boolean jump;

    // how close counts as arrived
    @Prop(min = 0) public double walkRadius = 0.5;

    @Prop(min = 0) public double walkSpeed = 4.317;
    @Prop(min = 0) public double sprintMultiplier = 1.3;
    @Prop(min = 0) public double sneakMultiplier = 0.3;

    // the speed you leave the ground at, not a height
    @Prop(min = 0) public double jumpPower = 8.4;

    @Prop(min = 0) public double gravityScale = 1.0;

    // the six below are the game's own movement, converted rather than chosen
    //
    // the game does not accelerate at all: it multiplies your speed by a friction each tick and adds
    // a fixed step, and the speed you end up at is where the two balance. on the ground the friction
    // is the block's 0.6 times 0.91 and the step is 0.1, so you settle at 0.1 / (1 - 0.546) = 0.22
    // blocks a tick -- 4.4 m/s, or the famous 4.317 once the 0.98 on walking forward is in
    //
    // the mover here accelerates instead, so what carries over is not the numbers but the *time*: a
    // decay by 0.546 a tick is within a tenth of where it is going after 0.19 seconds, either way.
    // so a straight line covering 4.317 m/s in 0.19 s is 22.7 m/s², and the same going down
    //
    // they were 36 and 56 before, which is a start half again as fast and a stop nearly three times
    // as fast. that is what made the walk feel like a different game's -- the top speed was right and
    // the two ends of it were not
    @Prop(min = 0) public double groundAcceleration = 22.7;
    @Prop(min = 0) public double groundDeceleration = 22.7;

    // the same sum in the air, where the friction is 0.91 and the step is 0.02: it settles at 4.44
    // m/s and takes 1.22 seconds to get there, so 3.64 m/s². air control is *slow* in this game and
    // that is most of what an air strafe feels like
    @Prop(min = 0) public double airSpeed = 4.444;
    @Prop(min = 0) public double airAcceleration = 3.64;

    // the fraction of speed a second of air leaves you with, so 1 is frictionless
    //
    // sideways and downward are not the same number in the game and were the same here: it drags
    // horizontal speed by 0.91 a tick and vertical by 0.98, which over a second is 0.15 and 0.67.
    // ours had the horizontal at 0.67 too, so a jump carried its run four times further than it
    // should have
    @Prop(min = 0, max = 1) public double airDrag = 0.1516;
    @Prop(min = 0, max = 1) public double fallDrag = 0.6676;

    @Prop(min = 0) public double stepHeight = 0.6;
    @Prop(min = 0, max = 90) public double slopeLimit = 45.0;
    @Prop(min = 0) public double slideAcceleration = 32.0;

    @Prop(min = 0) public double coyoteTime = 0.15;
    @Prop(min = 0) public double jumpBuffer = 0.15;

    public boolean followSlopes = true;
}
