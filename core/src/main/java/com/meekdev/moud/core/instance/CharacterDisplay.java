package com.meekdev.moud.core.instance;

public enum CharacterDisplay {

    // the volume that actually collides, drawn. what you see is what you hit, which is the one
    // thing a body made to look right can never promise
    HITBOX,

    // the rig hanging off the character
    MODEL,

    // nothing, which is what your own eyes want
    HIDDEN
}
