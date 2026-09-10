package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// what a player controls. these are the movement profile, in metres and seconds like everything
// else, and the adapter is the only thing that knows bkun states the same numbers per tick
//
// the shape is ours and is not vanilla's 0.6 box. the mover is a capsule, so a box character
// leaves its corners outside the collision and clips walls on a diagonal -- we declare the capsule
// and draw a body that fits it instead
public final class Character extends Spatial {

    @Prop(min = 0.05) public double radius = 0.3;
    @Prop(min = 0.1) public double height = 1.8;

    // what is drawn where the character is
    public CharacterDisplay display = CharacterDisplay.MODEL;

    // the rig, over the proportions the engine built it at. the capsule is radius and height and
    // does not follow this: a place that wants a bigger character says so on both, deliberately
    @Prop(min = 0.05) public double scale = 1.0;

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
