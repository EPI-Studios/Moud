package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;

// what holds one part onto another and lets it turn there
//
// a limb used to be posed by writing its whole frame, which meant every pose had to know where the
// joint stood and put it back itself. getting that wrong sent an arm to the character's feet and
// nothing said so. here the two belong to different owners: the engine states where the joint is,
// a pose states the turn at it, and neither can destroy the other by writing its own
//
//     part1.cframe = c0 . transform . inverse(c1)
public final class Joint extends Instance {

    // what it hangs off, and what hangs off it
    public Instance part0;
    public Instance part1;

    // where the joint sits on each of them
    //
    // c0 is the engine's: it is the shoulder line, and it moves when the body is rescaled. c1 is
    // usually nothing, because a limb is authored with its own origin already at its joint
    public CFrame c0 = CFrame.IDENTITY;
    public CFrame c1 = CFrame.IDENTITY;

    // the turn at the joint, which is the whole of posing a body. a place writes this, and cannot
    // lose the joint by writing it
    public CFrame transform = CFrame.IDENTITY;

    // how big the limb this holds is drawn, over whatever the body's own scale made it
    //
    // it grows about the joint rather than about the middle of the box, so a head twice the size
    // is still joined at the neck. that is what makes a big headed character and not a floating
    // one, and it is per axis because a limb is a box
    public Vec3 scale = Vec3.ONE;
}
