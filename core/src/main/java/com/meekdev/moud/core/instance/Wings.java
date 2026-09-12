package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// a pair of wings a body wears
//
// not a part, because there are two of them and they share one state: one is written and the
// other is its mirror. so this holds what they are doing and the two boxes hold what is drawn
//
// it lives beside the cape rather than on the character for the same reason the cape does. a body
// has a shape and a state; what it wears has its own, and a class that carries both runs out of
// the sixty four properties a dirty mask holds
public final class Wings extends Instance {

    public boolean worn;

    // where they are held, in radians
    //
    // the game smooths these on the entity rather than deriving them per frame: they ease toward
    // a target at 0.3 a tick, so a body that starts to fly opens them over half a second rather
    // than snapping them out
    public double x;
    public double y;
    public double z;

    // the sheet they are cut from. empty means the wearer's own elytra, then their cape, then
    // the game's
    @Prop(asset = true) public String skin = "";
}
