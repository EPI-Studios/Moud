package com.meekdev.moud.script.api;

// what the player is doing, as the place is allowed to ask it
//
// actions are named, never keys: a place that asks for "jump" keeps working when the player
// rebinds it, and 8.3 rule 3 keeps minecraft's key mapping type out of the api
public interface InputRef {

    boolean down(String action);

    // since the last frame, in the units the mouse produced. yaw and pitch are the camera's job
    double mouseX();

    double mouseY();

    // names an unknown action so a typo is an error rather than a key that never fires
    boolean known(String action);
}
