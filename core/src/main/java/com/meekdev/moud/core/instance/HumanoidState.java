package com.meekdev.moud.core.instance;

// what a body is doing, as one word
//
// six booleans that can all be true at once is six ways to be wrong. this is the state the engine
// writes every tick and a place may force -- forcing it is how you sit someone down or knock them
// out without inventing a flag for it
public enum HumanoidState {

    STANDING,

    RUNNING,

    JUMPING,

    // off the ground and not on the way up
    FALLING,

    SWIMMING,

    FLYING,

    // put somewhere by a place: a chair, a mount, a cutscene
    SEATED,

    // no life left. the engine stops driving a body in this state
    DEAD
}
