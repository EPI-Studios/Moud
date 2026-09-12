package com.meekdev.moud.core.instance;

// what you see of your own body when the camera is inside its head
//
// the game has one answer to this and it is a hand. a place may want any of the four, and which one
// is a property of the body rather than a switch the whole place throws, so two bodies in one place
// can answer differently -- a spider does not have a hand to hold up
public enum FirstPerson {

    // our own arm, drawn in the pass the game keeps for one. the rest of the body is not drawn,
    // because the camera is inside its head
    ARM,

    // the game's own hand, with the game's own animations and whatever it is holding. a place that
    // wants an item in a real hand wants this one, and ours steps aside
    HAND,

    // all of it, seen from inside its own head -- look down and you are standing there. this is the
    // one that needs nothing special: the camera sits in the head and the head's own faces are
    // pointing away, so being inside it shows nothing and everything below it shows
    BODY,

    // nothing at all, for a place that draws its own
    NONE
}
