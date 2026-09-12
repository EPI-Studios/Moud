package com.meekdev.moud.core.instance;

// how a body looks, beside the body itself
//
// a character is a shape: limbs, joints, what it carries, how big it is. what it looks like is a
// different question with a different owner -- the sheet it is cut from is a thing this client can
// see and the server cannot, and what you see of your own body is nobody's business but your own.
// keeping the two apart is also what keeps a character from being a class that does everything,
// which it was on its way to being
public final class Appearance extends Instance {

    // the sheet the body is cut from. empty means the wearer's own, and failing that the game's
    public String skin = "";

    // whether the arms are drawn a texel narrower. it belongs to the sheet and not to the body:
    // nothing a slim body collides with is any different
    public boolean slim;

    // a pair cut from the wearer's own sheet
    public boolean ears;

    // the model, the volume it really collides with, or nothing. read where the body is drawn --
    // it is a render policy and it says nothing about what a ray can reach, or about which limbs
    // a place has chosen to hide
    public CharacterDisplay display = CharacterDisplay.MODEL;

    // what you see of this body when you are the one wearing it
    public FirstPerson firstPerson = FirstPerson.ARM;
}
