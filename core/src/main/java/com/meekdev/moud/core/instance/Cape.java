package com.meekdev.moud.core.instance;

// a cape is a thing a body wears, not a fact about the body
//
// it started as five properties on Character and pushed that class past the sixty four a dirty
// mask holds -- which is the guard saying what it was built to say. a body has a shape and a
// state; what it wears has its own
public final class Cape extends Limb {

    // how it hangs, in the model's own degrees
    //
    // none of these are derived from where the body is. the game drags a second, lagging position
    // behind the player and reads the gap, so a cape lifts when you fall, flares when you run and
    // swings out when you turn, all from one vector nobody animates
    public double flap;
    public double lean;
    public double sway;
}
