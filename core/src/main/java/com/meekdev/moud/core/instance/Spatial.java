package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;

// a local frame, the world transform is the parent chain composed
public class Spatial extends Instance {

    public CFrame cframe = CFrame.IDENTITY;
    public boolean visible = true;

    // the point the frame turns about, from the middle of the thing outward
    //
    // a limb rotates at the shoulder and a door at its hinge, neither at its own centre. without
    // this a rig needs an empty frame per joint, which is a whole concept to carry for an offset
    public Vec3 pivot = Vec3.ZERO;

    // whether every client holds this whether or not it is anywhere near them
    //
    // §10.1: interest is per player and by distance, so the thing a player is standing in front of is
    // in their copy and the thing four hundred metres away is not. this is the exemption -- the sky, a
    // boundary, a scoreboard, whatever a place decided is part of the place rather than part of a
    // place's furniture. roblox spells the same thing Persistent on a model's streaming mode
    //
    // it is read off the top of a subtree only, and it carries the subtree with it: a thing hanging
    // off something relevant is relevant. a child with no parent in your copy is not a thing that can
    // be put anywhere, so interest is decided for a whole branch or for none of it -- which is what
    // roblox calls an atomic model, and what our tree being the transform hierarchy forces anyway
    public boolean alwaysRelevant;
}
