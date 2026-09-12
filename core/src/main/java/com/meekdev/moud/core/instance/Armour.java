package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// what a body is wearing on each of its four slots
//
// one sheet per slot, because a body wears an iron helmet over a diamond chestplate as readily as
// a matching set. empty means that slot is bare
//
// the boxes themselves live on the limbs they cover, the way the second skin layer does: a piece
// of armour is the limb again, a little bigger, off a different sheet. so it swings, crouches and
// scales with the limb without being told to
public final class Armour extends Instance {

    @Prop(driven = true, asset = true) public String head = "";
    @Prop(driven = true, asset = true) public String chest = "";
    @Prop(driven = true, asset = true) public String legs = "";
    @Prop(driven = true, asset = true) public String feet = "";

    // a skull, a pumpkin or somebody's head, which is head equipment the game draws as a box of
    // its own rather than as a piece of armour. wearing one takes the helmet's place
    @Prop(driven = true, asset = true) public String hat = "";

    // a player's or a zombie's head has a second layer over it, the way a skin does. a creeper's
    // or a skeleton's does not
    @Prop(driven = true) public boolean hatLayered;

    // all of it, when a player is wearing the body: what somebody has on their head and chest is
    // equipment, and the game already sends a player's equipment to everybody who can see them. a
    // place that dresses one of its own characters is the only one saying so, so that one replicates
    @Override
    protected long propertiesFromElsewhere() {
        return parent() instanceof Character body && body.worn() ? def().driven() : 0;
    }
}
