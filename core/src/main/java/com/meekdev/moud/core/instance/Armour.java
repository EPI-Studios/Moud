package com.meekdev.moud.core.instance;

// what a body is wearing on each of its four slots
//
// one sheet per slot, because a body wears an iron helmet over a diamond chestplate as readily as
// a matching set. empty means that slot is bare
//
// the boxes themselves live on the limbs they cover, the way the second skin layer does: a piece
// of armour is the limb again, a little bigger, off a different sheet. so it swings, crouches and
// scales with the limb without being told to
public final class Armour extends Instance {

    public String head = "";
    public String chest = "";
    public String legs = "";
    public String feet = "";

    // a skull, a pumpkin or somebody's head, which is head equipment the game draws as a box of
    // its own rather than as a piece of armour. wearing one takes the helmet's place
    public String hat = "";

    // a player's or a zombie's head has a second layer over it, the way a skin does. a creeper's
    // or a skeleton's does not
    public boolean hatLayered;
}
