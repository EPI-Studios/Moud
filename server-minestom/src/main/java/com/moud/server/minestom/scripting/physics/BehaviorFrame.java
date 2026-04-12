package com.moud.server.minestom.scripting.physics;


public final class BehaviorFrame {
    double speed;
    double acceleration;
    double deceleration;
    double airControl;
    double jumpVelocity;
    double gravityScale;
    double velocityX;
    double velocityY;
    double velocityZ;
    double wallNormalX;
    double wallNormalZ;
    boolean jumpRequested;
    boolean onFloorPrev;
    boolean onWallPrev;
    boolean onCeilingPrev;
    boolean floorBeforeMove;
    boolean jumpWasDown;
    boolean sprintWasDown;
    boolean sneakWasDown;
}
