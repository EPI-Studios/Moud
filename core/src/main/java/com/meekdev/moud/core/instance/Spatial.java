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

    // which player may write this, and whose client may write it locally
    //
    // §10.1: an instance may be assigned to a player, that client writes its owned properties
    // locally, and a client writing a replicated property it does not own is a luau error. empty
    // means the server owns it, which is almost everything
    //
    // it carries down the branch: a limb of a body you own is yours, a crate parented to a cart you
    // are pushing is yours. the nearest one up the chain wins, so handing over a cart hands over
    // what is on it, which is what makes a push feel instant
    //
    // roblox keeps this off to the side, as SetNetworkOwner on a part, and it is not replicated as a
    // property there. a property is the better answer here for one reason: the client has to know who
    // owns a thing to be told off for writing it, and a property already crosses
    //
    // on a character it is the player wearing the body -- §10.1 says characters are owned by their
    // player, so those are the same sentence rather than two fields that can disagree
    public String owner = "";
}
