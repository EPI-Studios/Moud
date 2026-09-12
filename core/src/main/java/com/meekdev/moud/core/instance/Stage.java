package com.meekdev.moud.core.instance;

// the stages a tick walks a tree in, in the order it walks them
//
// a tick is not one pass. each stage is a pass, and the order between them is the whole of the
// engine's control flow: a body cannot pose before it knows what state it is in, and it cannot
// compose before it is posed. before this that order lived as a hand written sequence of static
// calls in two files, which is a thing that drifts the moment a third file wants in
//
// a class takes part by overriding the method of the same name, and ClassDef notices once. nothing
// registers, so a class an addon writes joins the tick without the tick learning about it
public enum Stage {

    // geometry out of parameters -- the size of a limb from the body's scale, the box of an armour
    // plate from the limb it covers. only what an input changed, and never a field a place owns
    SHAPE,

    // where a body is going and what state that puts it in. the living half, before anything is
    // posed for it
    DRIVE,

    // tracks blended into the motors they name. this is where a walk cycle happens
    EVALUATE,

    // the joints physics drives instead of the animator. exclusive with evaluate, per joint: that
    // exclusivity is what makes a ragdoll a change of class rather than a flag branched on
    SIMULATE,

    // joints into the frames the renderer reads. the only writer of a part a joint drives
    COMPOSE;

    // held rather than calling values(), which copies
    public static final Stage[] ORDER = values();

    public final int bit = 1 << ordinal();

    // the method a class overrides to take part, which is this name lowercased
    public String method() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    // whether the hook is handed the step. the two that advance something in time are, the three
    // that derive one thing from another are not
    public boolean timed() {
        return this == DRIVE || this == SIMULATE;
    }
}
