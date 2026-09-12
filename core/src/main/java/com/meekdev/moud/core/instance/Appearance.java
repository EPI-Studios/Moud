package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// how a body looks, beside the body itself
//
// a character is a shape: limbs, joints, what it carries, how big it is. what it looks like is a
// different question with a different owner -- the sheet it is cut from is a thing this client can
// see and the server cannot, and what you see of your own body is nobody's business but your own.
// keeping the two apart is also what keeps a character from being a class that does everything,
// which it was on its way to being
public final class Appearance extends Instance {

    // the sheet the body is cut from. empty means the wearer's own, and failing that the game's
    @Prop(asset = true) public String skin = "";

    // whether the arms are drawn a texel narrower. it belongs to the sheet and not to the body:
    // nothing a slim body collides with is any different
    @Prop(driven = true) public boolean slim;

    // a pair cut from the wearer's own sheet
    @Prop(driven = true) public boolean ears;

    // the model, the volume it really collides with, or nothing. read where the body is drawn --
    // it is a render policy and it says nothing about what a ray can reach, or about which limbs
    // a place has chosen to hide
    //
    // not replicated, and that is the point of it rather than an omission: how a body is drawn is a
    // question each client answers for itself, the way a place turns on a debug view for whoever
    // pressed the key. a replicated one would be a place and a client taking turns writing the same
    // field, and the client would lose on whatever tick the server next touched it
    @Prop(replicated = false) public CharacterDisplay display = CharacterDisplay.MODEL;

    // what you see of this body when you are the one wearing it
    @Prop(replicated = false) public FirstPerson firstPerson = FirstPerson.ARM;

    // the two marked driven are read off the wearer rather than chosen: whether that player picked
    // the slim model, and whether they are the one person the game gives ears to. any client that
    // can see the player can see both, so sending them would be telling it what it knows
    @Override
    protected long propertiesFromElsewhere() {
        return parent() instanceof Character body && body.worn() ? def().driven() : 0;
    }
}
