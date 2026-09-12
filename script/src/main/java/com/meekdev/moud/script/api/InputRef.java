package com.meekdev.moud.script.api;

// what the player is doing, as the place is allowed to ask it
//
// actions are named, never keys: a place that asks for "jump" keeps working when the player
// rebinds it, and 8.3 rule 3 keeps minecraft's key mapping type out of the api
public interface InputRef {

    boolean down(String action);

    // where the pointer is, in the same units worldToScreen answers in and screenToRay asks for,
    // so the three compose without a conversion nobody would guess at
    //
    // it used to be the movement since the last frame under these names, which reads as a
    // position everywhere it is used and is not one
    double mouseX();

    double mouseY();

    // how far it moved since the last frame. yaw and pitch are still the camera's job
    double mouseDeltaX();

    double mouseDeltaY();

    // what screenToRay and worldToScreen are measured against. without it a place cannot name the
    // middle of the screen, which is the one point it always wants
    double screenWidth();

    double screenHeight();

    // names an unknown action so a typo is an error rather than a key that never fires
    boolean known(String action);

    // the pointer is captured for looking around, or released for a cursor to click with. a place
    // that draws ui has to be able to let go of it
    boolean mouseLocked();

    void lockMouse(boolean locked);

    double sensitivity();

    void sensitivity(double value);
}
