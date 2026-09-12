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
}
