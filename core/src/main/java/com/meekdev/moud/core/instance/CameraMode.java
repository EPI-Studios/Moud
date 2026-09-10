package com.meekdev.moud.core.instance;

public enum CameraMode {

    // the eye is the character's, and looking around is the player's own business
    FIRST_PERSON,

    // behind the character by distance, still aimed where the player is looking
    THIRD_PERSON,

    // the place owns it. cframe is the camera, nothing else touches it
    SCRIPTABLE
}
