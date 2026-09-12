package com.meekdev.moud.core.instance;

// what an arm is doing with what it holds
//
// the two flags are the model's own and they decide the order the two arms are posed in, not how
// either one looks. two handed means the other arm is dragged into it -- a bow needs both, so the
// offhand's own pose is thrown away. affecting the offhand means this pose already wrote the
// other arm, so posing that one afterwards would undo it
//
// a place writes these like any other property, so a body can draw a bow without holding one
public enum ArmPose {

    EMPTY(false, false),

    // anything held that has no pose of its own
    ITEM(false, false),

    // a shield up
    BLOCK(false, false),

    BOW(true, true),

    TRIDENT(false, true),

    CROSSBOW_CHARGE(true, true),

    CROSSBOW_HOLD(true, true),

    SPYGLASS(false, false),

    HORN(false, false),

    BRUSH(false, false);

    private final boolean twoHanded;
    private final boolean affectsOther;

    ArmPose(boolean twoHanded, boolean affectsOther) {
        this.twoHanded = twoHanded;
        this.affectsOther = affectsOther;
    }

    public boolean twoHanded() {
        return twoHanded;
    }

    public boolean affectsOther() {
        return affectsOther;
    }
}
