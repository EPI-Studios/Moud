package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
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
// a plain joint is rigid, which is why there is no separate weld: nothing writes its transform but
// whoever made it, and that is what holds a cape onto a back or a sword into a hand. the kinds below
// it are the ones something else is allowed to write -- a Motor the animator may pose, a Socket
// physics may throw about
public class Joint extends Instance {

    private static final PropertyDef CFRAME = Classes.SPATIAL.property("cframe");

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

    // the one writer of the frame of whatever it holds
    //
    //     world(part1) = world(part0) . c0 . transform . inverse(c1)
    //
    // stated in the space part1's frame is stated in, which is its parent's. that is the whole of
    // it: there is no case for the body's own joint and no case for a cape, and the rule that used
    // to say "if part0 is the character" is now just where part1 happens to hang
    @Override
    protected void compose() {
        if (!(part1 instanceof Spatial held) || !held.isAlive()) return;
        if (part0 == null || !part0.isAlive()) return;
        Instances.setObj(held, CFRAME, base().mul(c0).mul(transform).mul(inverse(c1)));
    }

    private CFrame base() {
        Instance under = part1.parent();
        // it hangs off what holds it -- a cape under a torso. the holder's frame is already in the
        // chain above it, and putting it here again would lay the cape down twice
        if (under == part0) return CFrame.IDENTITY;
        // it hangs beside what holds it -- a limb beside the body's root frame. one frame between
        // them, and everything above cancels
        if (under == part0.parent()) return Transforms.local(part0);
        // held by something in another branch, so there is nothing to cancel: the long way round.
        // this is what a seat or a grab across two bodies is
        return Transforms.world(under).inverse().mul(Transforms.world(part0));
    }

    // c1 is almost always nothing, so this almost always costs a comparison
    private static CFrame inverse(CFrame frame) {
        return frame.equals(CFrame.IDENTITY) ? CFrame.IDENTITY : frame.inverse();
    }
}
