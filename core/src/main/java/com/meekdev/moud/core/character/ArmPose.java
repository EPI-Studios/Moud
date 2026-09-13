package com.meekdev.moud.core.character;

public enum ArmPose {

    EMPTY(false, false),

    ITEM(false, false),

    BLOCK(false, false),

    BOW(true, true),

    TRIDENT(false, true),

    CROSSBOW_CHARGE(true, true),

    CROSSBOW_HOLD(true, true),

    SPYGLASS(false, false),

    HORN(false, false),

    BRUSH(false, false),

    SPEAR(false, true);

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
